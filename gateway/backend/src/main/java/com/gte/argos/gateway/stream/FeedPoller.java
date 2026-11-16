package com.gte.argos.gateway.stream;

import com.gte.argos.gateway.alarm.AlarmService;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.CentralDto.FeedItem;
import com.gte.argos.gateway.central.CentralDto.FeedPage;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
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
    private final boolean enabled;

    private String bootId;
    private long after = -1;
    private Levels lastLevels;
    private boolean warned;

    public FeedPoller(CentralClient central, MasterCache masterCache, StreamHub hub, AlarmService alarms,
                      ChecklistRepository checklists, SimTime time,
                      @Value("${argos.central.feed-enabled:true}") boolean enabled) {
        this.central = central;
        this.masterCache = masterCache;
        this.hub = hub;
        this.alarms = alarms;
        this.checklists = checklists;
        this.time = time;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 3000)
    public synchronized void poll() {
        if (!enabled) {
            return;
        }
        try {
            FeedPage page = central.feed(Math.max(after, 0), 500);
            if (after < 0 || !Objects.equals(bootId, page.bootId())) {
                boolean restarted = bootId != null;
                bootId = page.bootId();
                after = page.latest(); // 지난 것은 건너뛰고 지금부터
                lastLevels = Levels.of(central.status(null));
                if (restarted) {
                    hub.publish("reset", Map.of("reason", "CENTRAL_RESTARTED"), s -> true);
                }
                warned = false;
                return;
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
        } catch (RuntimeException e) {
            if (!warned) {
                log.warn("중앙 서버 피드를 가져오지 못했습니다: {}", e.getMessage());
                warned = true;
            }
        }
    }

    @Scheduled(fixedRate = 15_000)
    public void ping() {
        if (hub.size() > 0) {
            hub.publish("ping", Map.of(), s -> true);
        }
    }

    // ---------------------------------------------------------------

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
                hub.publish("alarm.updated", id, view, s -> s.scope().canProcess(a.processId(), masterCache.get()));
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
}
