package com.gte.argos.factory.ingest;

import com.gte.argos.factory.command.FanAutoService;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.judge.EdgeStage;
import com.gte.argos.factory.judge.JudgementEngine;
import com.gte.argos.factory.judge.SensorDef;
import com.gte.argos.factory.judge.SensorReading;
import com.gte.argos.factory.judge.Stage;
import com.gte.argos.factory.live.FeedingListener;
import com.gte.argos.factory.live.LiveFeed;
import com.gte.argos.factory.live.LiveState;
import com.gte.argos.factory.master.SensorDefinitionLoader;
import com.gte.argos.factory.retention.SensorDailyService;
import com.gte.argos.factory.store.JdbcJudgementStore;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실시간 센서값 입구. 시연 시작(11/16 09:00) 이후의 값이 여기로 들어온다.
 *
 * <pre>
 * 입력 어댑터
 *   개발용 POST(/dev/readings) → ingest() → 판정 엔진이 단계까지 판정
 *   엣지 Kafka argos.edge.raw → ingestEdgeValue() → 1분 추이·현재값만 (판정 안 함)
 *   엣지 Kafka argos.edge.stage → ingestEdgeStage() → 엣지 단계로 센서 이벤트, 관제 단위 조합 경보
 *   VMS 열화상 → ingestExternal() → VMS 단계로 이벤트·경보
 *   → DB 저장 + 메모리(LiveState: 지금 값, 최근 1시간) + 피드(LiveFeed: 중앙 서버가 가져감)
 * 엣지·VMS 값의 시각은 받은 시각(서버 시계)으로 한다 (보낸 쪽 ts 는 쓰지 않음, 2026-11-20 결정)
 * </pre>
 *
 * 실시간 모드에서도 경보에 묶인 이벤트가 모두 끝나면(값이 정상으로 돌아오면) 경보를 자동 종료한다 (AUTO_RECOVERED,
 * 2026-11-20). 사람이 먼저 닫을 수도 있다.
 */
@Service
public class LiveJudgementService {

    private static final Logger log = LoggerFactory.getLogger(LiveJudgementService.class);

    private final JdbcTemplate jdbc;
    private final SensorDefinitionLoader sensorLoader;
    private final DemoClock clock;
    private final LiveState state;
    private final LiveFeed feed;
    private final SensorDailyService daily;
    private final FanAutoService fanAuto;
    private final ApplicationEventPublisher events;

    private JudgementEngine engine;
    private FeedingListener listener;
    private Map<String, Long> sensorIdByTag = Map.of();

    /** 엣지(Kafka)가 단계를 판정하는지. 다시 켤 때 열린 이벤트를 밖 이벤트로 이어 받을지 정한다 */
    @org.springframework.beans.factory.annotation.Value("${argos.kafka.enabled:false}")
    private boolean kafkaEnabled;

    public LiveJudgementService(JdbcTemplate jdbc, SensorDefinitionLoader sensorLoader, DemoClock clock,
                                LiveState state, LiveFeed feed, SensorDailyService daily, FanAutoService fanAuto,
                                ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
        this.fanAuto = fanAuto;
        this.daily = daily;
        this.sensorLoader = sensorLoader;
        this.clock = clock;
        this.state = state;
        this.feed = feed;
    }

    /** 값 여러 개를 시간 순서대로 판정·저장한다. 판정에 넣은 수를 돌려준다 */
    @Transactional
    public synchronized int ingest(Collection<SensorReading> readings) {
        JudgementEngine e = engine();
        List<Object[]> values = new ArrayList<>();
        int accepted = 0;
        for (SensorReading r : readings) {
            if (r.ts().isBefore(clock.simStart())) {
                continue; // 시연 시작 전 값은 7일치 몫
            }
            Long sensorId = sensorIdByTag.get(r.tag());
            if (sensorId != null) {
                state.record(sensorId, r.ts(), r.value());
                values.add(new Object[] {sensorId, odt(r.ts()), r.value()});
            }
            e.accept(r);
            accepted++;
        }
        listener.flush();
        afterJudge();
        if (!values.isEmpty()) {
            feed.append("SENSOR_VALUES", clock.nowOffset(), Map.of("values", values));
        }
        return accepted;
    }

    /**
     * 밖에서 단계를 판정한 값 하나 (VMS 열화상). 판정 엔진은 받은 단계로 이벤트·경보만 만든다.
     *
     * @return 판정에 넣었으면 true (시연 시작 전 값·모르는 태그면 false)
     */
    @Transactional
    public synchronized boolean ingestExternal(SensorReading r, Stage stage) {
        if (r.ts().isBefore(clock.simStart())) {
            return false;
        }
        JudgementEngine e = engine();
        Long sensorId = sensorIdByTag.get(r.tag());
        if (sensorId == null) {
            return false;
        }
        state.record(sensorId, r.ts(), r.value());
        e.acceptExternal(r, stage);
        listener.flush();
        afterJudge();
        feed.append("SENSOR_VALUES", clock.nowOffset(),
                Map.of("values", List.<Object[]>of(new Object[] {sensorId, odt(r.ts()), r.value()})));
        return true;
    }

    /**
     * 엣지(DAS) 1초 값 (Kafka argos.edge.raw). 시각은 받은 시각(서버 시계)으로 한다 (엣지 ts 는 쓰지 않음).
     * 단계 판정은 하지 않는다 (엣지가 판정해 argos.edge.stage 로 보냄).
     *
     * @param value null 이거나 lost 면 통신 끊김(COMM_LOST)으로 센다
     * @return 아는 태그면 true
     */
    @Transactional
    public synchronized boolean ingestEdgeValue(String tag, Double value, boolean lost) {
        JudgementEngine e = engine();
        Long sensorId = sensorIdByTag.get(tag);
        if (sensorId == null) {
            return false;
        }
        java.time.Instant now = clock.now();
        if (lost || value == null) {
            e.acceptLost(tag, now);
            listener.flush();
            return true;
        }
        state.record(sensorId, now, value);
        e.acceptValue(new SensorReading(tag, now, value));
        listener.flush();
        feed.append("SENSOR_VALUES", clock.nowOffset(),
                Map.of("values", List.<Object[]>of(new Object[] {sensorId, odt(now), value})));
        return true;
    }

    /**
     * 엣지(DAS) 단계 변경 (Kafka argos.edge.stage) → 센서 이벤트, 관제 단위 조합 경보, 가스면 배기팬 자동 제어.
     * s.ts() 는 받은 시각(서버 시계)이어야 한다.
     */
    @Transactional
    public synchronized void ingestEdgeStage(EdgeStage s) {
        engine().acceptEdgeStage(s);
        listener.flush();
        afterJudge();
    }

    /** 판정 뒤: 가스 경보면 배기팬 자동 제어, 값이 돌아와 자동으로 닫힌 경보면 배기팬 복귀 · 승인 대기 취소 */
    private void afterJudge() {
        for (FeedingListener.GasAlarm g : listener.drainGasAlarms()) {
            fanAuto.onGasAlarm(g.alarmId(), g.unitId(), g.stage());
        }
        for (Long alarmId : listener.drainClosedAlarms()) {
            log.info("경보 {} 자동 종료 (값이 정상으로 돌아옴)", alarmId);
            fanAuto.onAlarmClosed(alarmId);
        }
    }

    /** 이 센서의 지금 단계 (진행 중 이벤트 기준) */
    public synchronized Stage currentStage(String tag) {
        return engine().currentStage(tag);
    }

    /** 사람이 경보를 닫았을 때. 이후 그 단위의 새 이벤트는 새 경보가 된다 */
    public synchronized void closeAlarm(long unitId) {
        engine().closeAlarm(unitId);
        listener.alarmClosed(unitId);
    }

    /**
     * 실시간 리셋: 7일치 더미가 끝나는 시각(simStart, 11/16 09:00) 이후 생긴 판정 결과를 지운 뒤 엔진을 새로 만든다.
     * 7일치(09:00 이전)는 그대로 둔다. 시계는 실제 서버 시각이라 되돌리지 않는다.
     */
    @Transactional
    public synchronized void reset() {
        OffsetDateTime cutoff = clock.simStart().atZone(clock.zone()).toOffsetDateTime();
        jdbc.update("DELETE FROM equipment_command WHERE requested_at >= ?", cutoff);
        jdbc.update("DELETE FROM alarm WHERE raised_at >= ?", cutoff);
        jdbc.update("DELETE FROM sensor_event WHERE started_at >= ? OR ended_at IS NULL", cutoff);
        jdbc.update("DELETE FROM sensor_trend WHERE ts >= ?", cutoff);
        daily.resetFromSimStart();
        jdbc.update("UPDATE equipment SET run_state = 'RUN', "
                + "fan_rpm = CASE WHEN equipment_type = 'FAN' THEN "
                + com.gte.argos.factory.command.EquipmentCommandService.HZ_NORMAL
                * com.gte.argos.factory.command.EquipmentCommandService.RPM_PER_HZ + " ELSE fan_rpm END");
        engine = null;
        listener = null;
        state.clear();
        clock.restart();
        feed.append("RESET", clock.nowOffset(), Map.of());
        log.info("실시간 리셋: {} 이후 판정 결과를 지우고 {} 부터 다시 시작", clock.simStart(), clock.nowOffset());
    }

    /** 센서·기준값을 바꾼 뒤 엔진에 반영한다. 진행 중 판정·경보는 그대로 이어진다 */
    public synchronized void reloadDefinitions() {
        // 엣지로 기준값 다시 보내기 (Kafka 켜짐일 때 ThresholdPublisher 가 커밋 뒤에 받음)
        events.publishEvent(new DefinitionsChanged());
        if (engine == null) {
            return; // 다음 값이 들어올 때 새 기준으로 만들어진다
        }
        List<SensorDef> sensors = sensorLoader.loadActive();
        Map<String, Long> ids = new HashMap<>();
        for (SensorDef s : sensors) {
            ids.put(s.tag(), s.sensorId());
        }
        sensorIdByTag = ids;
        engine.updateDefinitions(sensors);
    }

    /** 센서·기준값이 바뀜 */
    public record DefinitionsChanged() {
    }

    private JudgementEngine engine() {
        if (engine == null) {
            List<SensorDef> sensors = sensorLoader.loadActive();
            Map<String, Long> ids = new HashMap<>();
            for (SensorDef s : sensors) {
                ids.put(s.tag(), s.sensorId());
            }
            sensorIdByTag = ids;
            listener = new FeedingListener(new JdbcJudgementStore(jdbc, clock.zone(), 1), feed, clock.zone());
            // 값이 정상으로 돌아오면 경보도 자동 종료 (2026-11-20, 전에는 사람이 닫을 때까지 열어 둠)
            engine = new JudgementEngine(sensors, listener, new JudgementEngine.Options(6, true));
            restoreOpen();
        }
        return engine;
    }

    /**
     * 서버를 다시 켤 때 DB 에 열린 채 남은 이벤트 · 경보를 엔진에 다시 넣는다.
     * 전에는 엔진이 이걸 잊어서, 이어 오는 단계(VMS · 엣지)로 같은 상황의 경보가 다시 켤 때마다 새로 생겼다 (2026-11-20).
     * <pre>
     * - 센서마다 가장 최근의 열린 이벤트만 이어 받는다. 그 앞의 겹친 열린 이벤트는 최근 이벤트 시작 시각으로 닫는다
     *   (예전 재시작으로 생긴 중복. 닫아야 그 경보를 사람이 종료할 수 있다)
     * - 관제 단위마다 가장 최근의 열린 경보를 이어 받고, 그 경보에 묶인 열린 이벤트를 다시 묶는다
     * - VMS(rule IR) · 엣지(Kafka 켜짐) 이벤트는 밖 판정 이벤트로, 그 밖은 서버 판정 상태로
     * </pre>
     */
    private void restoreOpen() {
        Map<Long, String> tagById = new HashMap<>();
        sensorIdByTag.forEach((tag, id) -> tagById.put(id, tag));
        List<JudgementEngine.RestoredEvent> events = new ArrayList<>();
        Map<Long, Long> keyByEventId = new HashMap<>();
        Map<Long, Object> keptStart = new HashMap<>(); // 센서 → 이어 받은 이벤트의 시작 시각 (DB 값 그대로)
        int closed = 0;
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT event_id, sensor_id, stage, rule, peak_value, edge_key, started_at FROM sensor_event
                WHERE ended_at IS NULL ORDER BY sensor_id, started_at DESC, event_id DESC
                """)) {
            long sensorId = ((Number) r.get("sensor_id")).longValue();
            long eventId = ((Number) r.get("event_id")).longValue();
            String tag = tagById.get(sensorId);
            if (tag == null) {
                continue;
            }
            if (keptStart.containsKey(sensorId)) {
                jdbc.update("UPDATE sensor_event SET ended_at = GREATEST(?, started_at) WHERE event_id = ?",
                        keptStart.get(sensorId), eventId);
                closed++;
                continue;
            }
            keptStart.put(sensorId, r.get("started_at"));
            String rule = (String) r.get("rule");
            long key = engine.nextEventKey();
            events.add(new JudgementEngine.RestoredEvent(key, tag, stage(((Number) r.get("stage")).intValue()), rule,
                    r.get("peak_value") == null ? 0 : ((Number) r.get("peak_value")).doubleValue(),
                    (String) r.get("edge_key"), "IR".equals(rule) || kafkaEnabled));
            listener.restoreEvent(key, eventId, sensorId);
            keyByEventId.put(eventId, key);
        }
        List<JudgementEngine.RestoredAlarm> alarms = new ArrayList<>();
        java.util.Set<Long> units = new java.util.HashSet<>();
        List<Long> autoClosed = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT alarm_id, unit_id, stage, hazard FROM alarm WHERE status <> 'CLOSED'
                ORDER BY unit_id, raised_at DESC, alarm_id DESC
                """)) {
            long unitId = ((Number) r.get("unit_id")).longValue();
            long alarmId = ((Number) r.get("alarm_id")).longValue();
            if (!units.add(unitId)) {
                autoClosed.add(closeRecovered(alarmId, unitId)); // 그 단위의 더 최근 경보가 있음 (예전 재시작으로 겹친 경보)
                continue;
            }
            // 이 경보에 묶인 열린 이벤트 + 같은 단위 센서의 열린 이벤트 (예전 경보에 묶였던 것도 이 경보에 다시 묶는다)
            List<Long> linked = new ArrayList<>();
            for (Long eventId : jdbc.queryForList("""
                    SELECT event_id FROM alarm_event WHERE alarm_id = ?
                    UNION
                    SELECT e.event_id FROM sensor_event e
                    JOIN control_unit_sensor cus ON cus.sensor_id = e.sensor_id AND cus.unit_id = ?
                    WHERE e.ended_at IS NULL
                    """, Long.class, alarmId, unitId)) {
                Long key = keyByEventId.get(eventId);
                if (key != null) {
                    linked.add(key);
                    jdbc.update("INSERT INTO alarm_event (alarm_id, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                            alarmId, eventId);
                }
            }
            if (linked.isEmpty()) {
                autoClosed.add(closeRecovered(alarmId, unitId)); // 값이 이미 정상으로 돌아와 있음
                continue;
            }
            long key = engine.nextAlarmKey();
            Stage stage = stage(((Number) r.get("stage")).intValue());
            com.gte.argos.factory.judge.Hazard hazard = com.gte.argos.factory.judge.Hazard.valueOf((String) r.get("hazard"));
            alarms.add(new JudgementEngine.RestoredAlarm(key, unitId, stage, hazard, linked));
            listener.restoreAlarm(key, alarmId, unitId, stage, hazard);
        }
        engine.restore(events, alarms);
        if (!events.isEmpty() || !alarms.isEmpty() || closed > 0 || !autoClosed.isEmpty()) {
            log.info("열린 이벤트 {}건 · 경보 {}건을 이어 받음 (겹친 열린 이벤트 {}건 닫음, 값이 돌아온 경보 {}건 자동 종료 {})",
                    events.size(), alarms.size(), closed, autoClosed.size(), autoClosed);
        }
        for (Long alarmId : autoClosed) {
            fanAuto.onAlarmClosed(alarmId);
        }
    }

    /** 값이 정상으로 돌아온 열린 경보를 자동 종료 (AUTO_RECOVERED) 하고 피드로 알린다. 닫은 경보 id */
    private long closeRecovered(long alarmId, long unitId) {
        OffsetDateTime now = clock.nowOffset();
        jdbc.update("""
                UPDATE alarm SET status = 'CLOSED', closed_at = ?, close_reason = 'AUTO_RECOVERED'
                WHERE alarm_id = ? AND status <> 'CLOSED'
                """, now, alarmId);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("alarmId", alarmId);
        data.put("unitId", unitId);
        data.put("closed", true);
        feed.append("ALARM_CLEARED", now, data);
        return alarmId;
    }

    private static Stage stage(int code) {
        return code >= 3 ? Stage.ALARM : code == 2 ? Stage.WARN : Stage.NORMAL;
    }

    private OffsetDateTime odt(java.time.Instant i) {
        return i.atZone(clock.zone()).toOffsetDateTime();
    }
}
