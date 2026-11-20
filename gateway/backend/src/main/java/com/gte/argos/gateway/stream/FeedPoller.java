package com.gte.argos.gateway.stream;

import com.gte.argos.gateway.fire.FireService;

import com.gte.argos.gateway.alarm.AlarmService;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.CentralDto.FeedItem;
import com.gte.argos.gateway.central.CentralDto.FeedPage;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.RedisBridge;
import com.gte.argos.gateway.central.RedisKeys;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.checklist.ChecklistRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 중앙 서버 피드(/internal/feed)를 1초마다 가져와 SSE 이벤트로 바꿔 보낸다.
 *
 * <pre>
 * SENSOR_VALUES             → sensor.value   (그 공정을 보고 있는 사람만)
 * EVENT_* · ALARM_* · COMMAND_RESULT 가 있으면 중앙 현재 상태를 다시 읽어 바뀐 단계만 → level.changed
 * ALARM_OPENED              → alarm.created
 * ALARM_UPDATED · CLEARED · STATUS → alarm.updated
 * COMMAND_RESULT            → command.result
 * RESET (시연 리셋)          → reset (화면은 전부 다시 불러온다)
 * </pre>
 * 중앙 서버가 다시 켜지면(bootId 변경) 처음부터 다시 따라간다.
 *
 * (2026-11-20 아키텍처) ARGOS_REDIS_ENABLED=true 면 HTTP 폴링 대신 전용 스레드가 Redis 스트림 argos:feed 를
 * 기다려 읽는다 (새 변화가 오면 바로). 처리 방식은 같다.
 */
@Component
public class FeedPoller {

    private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);
    private static final Set<String> LEVEL_TYPES = Set.of("EVENT_OPENED", "EVENT_ESCALATED", "EVENT_CLOSED",
            "ALARM_OPENED", "ALARM_UPDATED", "ALARM_CLEARED", "COMMAND_RESULT", "RESET");

    private final CentralClient central;
    private final MasterCache masterCache;
    private final StreamHub hub;
    private final AlarmService alarms;
    private final ChecklistRepository checklists;
    private final SimTime time;
    private final FireService fires;
    private final com.gte.argos.gateway.fire.FireIds fireIds;
    private final boolean enabled;
    private final com.gte.argos.gateway.common.ActivityLogger activity;

    private RedisBridge redis;
    private volatile boolean running;
    private Thread redisReader;

    private String bootId;
    private long after = -1;
    private Levels lastLevels;
    private boolean warned;

    public FeedPoller(CentralClient central, MasterCache masterCache, StreamHub hub, AlarmService alarms,
                      ChecklistRepository checklists, SimTime time, FireService fires,
                      @Value("${argos.central.feed-enabled:true}") boolean enabled,
                      com.gte.argos.gateway.common.ActivityLogger activity,
                      com.gte.argos.gateway.fire.FireIds fireIds) {
        this.fireIds = fireIds;
        this.activity = activity;
        this.central = central;
        this.masterCache = masterCache;
        this.hub = hub;
        this.alarms = alarms;
        this.checklists = checklists;
        this.time = time;
        this.fires = fires;
        this.enabled = enabled;
    }

    /** Redis 를 켰을 때만 주입됨 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRedis(RedisBridge redis) {
        this.redis = redis;
    }

    @jakarta.annotation.PostConstruct
    void startRedis() {
        if (!enabled || redis == null) {
            return;
        }
        running = true;
        redisReader = new Thread(() -> {
            while (running) {
                if (!step((boot, a) -> redis.feed(boot, a, 500, java.time.Duration.ofSeconds(1)))) {
                    pause();
                }
            }
        }, "redis-feed-reader");
        redisReader.setDaemon(true);
        redisReader.start();
        log.info("수집 서버 피드를 Redis({})로 받습니다", RedisKeys.FEED);
    }

    @jakarta.annotation.PreDestroy
    void stopRedis() {
        running = false;
        if (redisReader != null) {
            redisReader.interrupt();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public void poll() {
        if (!enabled || redis != null) {
            return;
        }
        step((boot, a) -> central.feed(a, 500));
    }

    /** 피드 한 번 따라가기. 가져오기에 실패하면 false */
    synchronized boolean step(java.util.function.BiFunction<String, Long, FeedPage> fetch) {
        try {
            FeedPage page = fetch.apply(bootId, Math.max(after, 0));
            if (after < 0 || !Objects.equals(bootId, page.bootId())) {
                boolean restarted = bootId != null;
                bootId = page.bootId();
                after = page.latest(); // 지난 것은 건너뛰고 지금부터
                lastLevels = Levels.of(central.status(null));
                if (restarted) {
                    hub.publish("reset", Map.of("reason", "CENTRAL_RESTARTED"), s -> true);
                }
                try {
                    fires.sweepClosedAlarms(); // 건너뛴 피드에 있었을 자동 종료 경보의 화재 정리
                } catch (RuntimeException e) {
                    log.warn("화재 정리 실패: {}", e.getMessage());
                }
                warned = false;
                return true;
            }
            if (page.gap()) {
                hub.publish("reset", Map.of("reason", "GAP"), s -> true);
            }
            boolean levelDirty = false;
            for (FeedItem item : page.items()) {
                levelDirty |= LEVEL_TYPES.contains(item.type());
                handle(item);
            }
            after = page.next();
            if (levelDirty && hub.size() > 0) {
                publishLevelChanges(central.status(null));
            } else if (levelDirty) {
                lastLevels = Levels.of(central.status(null));
            }
            warned = false;
            return true;
        } catch (RuntimeException e) {
            if (!warned) {
                log.warn("중앙 서버 피드를 가져오지 못했습니다: {}", e.getMessage());
                warned = true;
            }
            return false;
        }
    }

    @Scheduled(fixedRate = 15_000)
    public void ping() {
        if (hub.size() > 0) {
            hub.publish("ping", Map.of(), s -> true);
        }
    }

    // ---------------------------------------------------------------

    /**
     * 주의 알림: 값이 기준 위일 때 조치 확인을 눌러 열린 채 남은 알림은, 값이 기준 아래로 돌아오면 자동으로 닫는다.
     * (주의 창에는 종료 단추가 없으므로. 경보는 셧다운 뒤 '경보 종료'로 직접 닫는다)
     * 닫는 사람 = 체크리스트를 확인한 사람.
     */
    private void closeIfConfirmed(CentralDto.Alarm a) {
        if (a.valueStillOver() || a.closedAt() != null || a.stage() >= 3) {
            return;
        }
        ChecklistRepository.Checklist c = checklists.latestForAlarm(a.alarmId());
        if (c == null || !"CONFIRMED".equals(c.status())) {
            return;
        }
        try {
            central.closeAlarm(a.alarmId(), c.confirmedBy());
            checklists.addAction(a.alarmId(), "CLOSE", null, "OK", "값이 기준 아래로 돌아와 자동 종료",
                    c.confirmedBy(), time.now());
            log.info("주의 알림 {} 자동 종료 (체크리스트 {} 확인 뒤 값 회복)", a.alarmId(), c.id());
        } catch (RuntimeException e) {
            log.warn("주의 알림 {} 자동 종료 실패: {}", a.alarmId(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handle(FeedItem item) {
        Map<String, Object> d = item.data() == null ? Map.of() : item.data();
        String id = String.valueOf(item.seq());
        switch (item.type()) {
            case "SENSOR_VALUES" -> sensorValues((List<List<Object>>) d.get("values"), item.at());
            case "ALARM_OPENED" -> {
                CentralDto.Alarm a = central.alarm(num(d.get("alarmId")));
                Map<String, Object> view = alarms.items(List.of(a), masterCache.get()).get(0);
                hub.publish("alarm.created", id, view, s -> s.scope().canProcess(a.processId(), masterCache.get()));
                fireCheck(a);
                Long fireId = fireIds.byAlarm(a.alarmId());
                if (fireId != null) {
                    // 화재로 판정되면 그 알림에 fireId 를 붙여 다시 알린다
                    Map<String, Object> fv = new LinkedHashMap<>();
                    fv.put("id", a.alarmId());
                    fv.put("level", Codes.level(a.stage()));
                    fv.put("hazard", a.hazard());
                    fv.put("status", a.status());
                    fv.put("fireId", fireId);
                    fv.put("at", item.at());
                    hub.publish("alarm.updated", id + "-f", fv,
                            s -> s.scope().canProcess(a.processId(), masterCache.get()));
                }
            }
            case "ALARM_UPDATED", "ALARM_CLEARED", "ALARM_STATUS" -> {
                long alarmId = num(d.get("alarmId"));
                CentralDto.Alarm a = central.alarm(alarmId);
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("id", alarmId);
                view.put("level", Codes.level(a.stage()));
                view.put("hazard", a.hazard());
                view.put("status", a.status());
                view.put("valueStillOver", a.valueStillOver());
                if (d.get("by") != null) {
                    long by = num(d.get("by"));
                    view.put("by", checklists.userNames(List.of(by)).get(by));
                }
                view.put("at", item.at());
                boolean closed = "CLOSED".equals(a.status());
                view.put("closeReason", a.closeReason());
                if (!closed) {
                    fireCheck(a);
                }
                view.put("fireId", fireIds.byAlarm(alarmId));
                hub.publish("alarm.updated", id, view, s -> s.scope().canProcess(a.processId(), masterCache.get()));
                if (closed && "AUTO_RECOVERED".equals(a.closeReason()) && "ALARM_CLEARED".equals(item.type())) {
                    autoClosed(a);
                } else {
                    closeIfConfirmed(a);
                }
            }
            case "COMMAND_RESULT" -> {
                long equipmentId = num(d.get("equipmentId"));
                CentralDto.Equipment eq = masterCache.get().equipment(equipmentId);
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("commandId", d.get("commandId"));
                view.put("equipmentId", equipmentId);
                view.put("command", d.get("command"));
                view.put("status", d.get("status"));
                view.put("runState", Codes.runState((String) d.get("runState")));
                if (eq != null && "FAN".equals(eq.type())) {
                    int rpm = d.get("fanRpm") == null ? 0 : ((Number) d.get("fanRpm")).intValue();
                    view.put("fan", Map.of("power", rpm > 0 && "RUN".equals(d.get("runState")) ? "ON" : "OFF",
                            "rpm", rpm, "targetRpm", rpm));
                } else {
                    view.put("fan", null);
                }
                long processId = eq == null ? -1 : eq.processId();
                hub.publish("command.result", id, view, s -> s.scope().canProcess(processId, masterCache.get()));
                if (d.get("alarmId") != null) {
                    // 알림 창의 fanAuto.status 를 갱신하도록 그 알림도 다시 알린다
                    CentralDto.Alarm a = central.alarm(num(d.get("alarmId")));
                    Map<String, Object> av = new LinkedHashMap<>();
                    av.put("id", a.alarmId());
                    av.put("level", Codes.level(a.stage()));
                    av.put("hazard", a.hazard());
                    av.put("status", a.status());
                    av.put("valueStillOver", a.valueStillOver());
                    av.put("fanAuto", com.gte.argos.gateway.alarm.AlarmViews.fanAuto(a));
                    av.put("at", item.at());
                    hub.publish("alarm.updated", id + "-a", av, s -> s.scope().canProcess(a.processId(), masterCache.get()));
                    if ("AUTO".equals(d.get("source")) && "SENT".equals(d.get("status")) && d.get("approvedBy") == null
                            && eq != null) {
                        int hz = d.get("value") == null ? 0 : ((Number) d.get("value")).intValue();
                        activity.logAs(null, "FAN", masterCache.get().process(eq.processId()) == null ? null
                                        : plantOf(eq.processId()), eq.processId(), "equipment_command",
                                num(d.get("commandId")), autoFanText(eq.name(), hz, a.stage()));
                    }
                }
            }
            case "THRESHOLD_CHANGED" -> {
                // 마스터의 "지금 기준값" 이 바뀌었으니 캐시를 비우고 그 공정을 보는 화면에 알린다
                masterCache.invalidate();
                long equipmentId = num(d.get("equipmentId"));
                CentralDto.Equipment eq = masterCache.get().equipment(equipmentId);
                long processId = eq == null ? -1 : eq.processId();
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("equipmentId", equipmentId);
                view.put("processId", eq == null ? null : eq.processId());
                view.put("season", d.get("season"));
                view.put("at", item.at());
                hub.publish("threshold.changed", id, view, s -> s.scope().canProcess(processId, masterCache.get()));
            }
            case "RESET" -> {
                time.invalidate();
                masterCache.invalidate();
                hub.publish("reset", Map.of("reason", "SIM_RESET"), s -> true);
            }
            case "MASTER_CHANGED" -> {
                // 공장 관리에서 사업장·공장·공정·배치가 바뀜 → 마스터 캐시를 비우고 화면에 알림
                masterCache.invalidate();
                hub.publish("master.changed", id, d, s -> true);
            }
            default -> {
                // EVENT_* 는 level.changed 로만 알린다
            }
        }
    }

    /** [[sensorId, ts, value], …] → 공정별 sensor.value */
    private void sensorValues(List<List<Object>> values, OffsetDateTime at) {
        if (values == null || values.isEmpty() || hub.size() == 0) {
            return;
        }
        MasterCache.Snapshot m = masterCache.get();
        Map<Long, List<List<Object>>> byProcess = new HashMap<>();
        for (List<Object> v : values) {
            long sensorId = ((Number) v.get(0)).longValue();
            Long processId = m.processOfSensor(sensorId);
            if (processId != null) {
                byProcess.computeIfAbsent(processId, k -> new ArrayList<>())
                        .add(java.util.Arrays.asList(sensorId, v.get(2)));
            }
        }
        byProcess.forEach((processId, list) -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("processId", processId);
            data.put("at", at);
            data.put("values", list);
            hub.publish("sensor.value", data, s -> s.viewingProcess(processId)
                    && s.scope().canProcess(processId, masterCache.get()));
        });
    }

    /** 이전 상태와 비교해 바뀐 센서마다 level.changed (상위 단계 포함) */
    private void publishLevelChanges(CentralDto.Status status) {
        Levels now = Levels.of(status);
        Levels before = lastLevels;
        lastLevels = now;
        if (before == null) {
            return;
        }
        MasterCache.Snapshot m = masterCache.get();
        for (CentralDto.SensorStatus s : status.sensors()) {
            int prev = before.sensors.getOrDefault(s.sensorId(), 1);
            if (prev == s.stage()) {
                continue;
            }
            Long factoryId = m.factoryOfProcess(s.processId());
            Long siteId = factoryId == null ? null : m.plantOfFactory(factoryId);
            Map<String, Object> parents = new LinkedHashMap<>();
            parents.put("equipmentId", s.equipmentId());
            parents.put("equipmentLevel", Codes.level(now.equipment.getOrDefault(s.equipmentId(), 1)));
            parents.put("processId", s.processId());
            parents.put("processLevel", Codes.level(status.level("processes", s.processId())));
            parents.put("factoryId", factoryId);
            parents.put("factoryLevel", factoryId == null ? null : Codes.level(status.level("factories", factoryId)));
            parents.put("siteId", siteId);
            parents.put("siteLevel", siteId == null ? null : Codes.level(status.level("plants", siteId)));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("target", "SENSOR");
            data.put("id", s.sensorId());
            data.put("level", Codes.level(s.stage()));
            data.put("prev", Codes.level(prev));
            data.put("parents", parents);
            long processId = s.processId();
            hub.publish("level.changed", data, sub -> sub.scope().canSite(siteId == null ? -1 : siteId, m)
                    || sub.scope().canProcess(processId, m));
        }
    }

    private Long plantOf(long processId) {
        CentralDto.Process p = masterCache.get().process(processId);
        CentralDto.Factory f = p == null ? null : masterCache.get().factory(p.factoryId());
        return f == null ? null : f.plantId();
    }

    /** 이용 로그 내용: '상도 건조로 배기팬 45Hz · 1350rpm (가스 주의 자동)' */
    static String autoFanText(String fanName, int hz, int stage) {
        return fanName + " " + hz + "Hz · " + (hz * 30) + "rpm (가스 " + (stage >= 3 ? "경보" : "주의") + " 자동)";
    }

    private static long num(Object o) {
        return ((Number) o).longValue();
    }

    /** 단계 스냅샷 */
    private record Levels(Map<Long, Integer> sensors, Map<Long, Integer> equipment) {
        static Levels of(CentralDto.Status st) {
            Map<Long, Integer> s = new HashMap<>();
            Map<Long, Integer> e = new HashMap<>();
            st.sensors().forEach(x -> s.put(x.sensorId(), x.stage()));
            st.equipment().forEach(x -> e.put(x.equipmentId(), x.stage()));
            return new Levels(s, e);
        }
    }

    /** 값이 정상으로 돌아와 수집 서버가 경보를 자동 종료함: 조치 기록, 화재면 화재도 자동 해제 */
    private void autoClosed(CentralDto.Alarm a) {
        try {
            checklists.addAction(a.alarmId(), "CLOSE", null, "OK", "값이 정상으로 돌아와 자동 종료", null, time.now());
            fires.autoClear(a.alarmId());
        } catch (RuntimeException e) {
            log.warn("경보 {} 자동 종료 후처리 실패: {}", a.alarmId(), e.getMessage());
        }
    }

    /** 화재 판정 실패가 피드 처리를 멈추지 않게 */
    private void fireCheck(CentralDto.Alarm a) {
        try {
            fires.onAlarm(a);
        } catch (RuntimeException e) {
            log.warn("화재 판정 처리 실패 alarm={}: {}", a.alarmId(), e.getMessage());
        }
    }
}
