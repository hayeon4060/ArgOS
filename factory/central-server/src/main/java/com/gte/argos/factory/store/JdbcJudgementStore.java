package com.gte.argos.factory.store;

import com.gte.argos.factory.judge.Hazard;
import com.gte.argos.factory.judge.JudgementListener;
import com.gte.argos.factory.judge.Stage;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 판정 결과를 DB 에 저장한다.
 *
 * <pre>
 * - 1분 추이·이벤트 샘플은 batchSize 만큼 모아서 한 번에 넣는다 (7일치 = 큰 batch, 실시간 = 1)
 * - 이벤트·경보는 바로 넣고 받은 id 를 엔진 번호(eventKey, alarmKey)와 짝지어 둔다
 * - 다 쓴 뒤 flush() 를 꼭 불러야 남은 batch 가 들어간다
 * - 알림이 열려 있는 동안 그 관제 단위 센서 전부(IR 제외)의 값을 alarm_sensor_sample 에 남긴다
 *   (시작: 원인 이벤트 샘플을 옮겨 옴, 끝: 알림 종료 — 과거 데이터는 onAlarmCleared, 실시간은 forgetUnit)
 * </pre>
 *
 * 엔진 하나에 저장소 하나. 스레드 안전하지 않다.
 */
public class JdbcJudgementStore implements JudgementListener {

    private static final String INSERT_TREND = """
            INSERT INTO sensor_trend (sensor_id, ts, avg_v, min_v, max_v, n) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (sensor_id, ts) DO UPDATE
               SET avg_v = EXCLUDED.avg_v, min_v = EXCLUDED.min_v, max_v = EXCLUDED.max_v, n = EXCLUDED.n
            """;
    private static final String INSERT_EVENT = """
            INSERT INTO sensor_event (sensor_id, threshold_id, stage, max_stage, rule, started_at, peak_value, edge_key)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final DateTimeFormatter EDGE_TS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final String INSERT_ALARM_SAMPLE = """
            INSERT INTO alarm_sensor_sample (alarm_id, sensor_id, ts, value) VALUES (?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;
    private static final String INSERT_SAMPLE = """
            INSERT INTO sensor_event_sample (event_id, ts, value) VALUES (?, ?, ?)
            ON CONFLICT (event_id, ts) DO NOTHING
            """;

    private final JdbcTemplate jdbc;
    private final ZoneId zone;
    private final int batchSize;

    private final List<Object[]> trendBatch = new ArrayList<>();
    private final List<Object[]> sampleBatch = new ArrayList<>();
    private final List<Object[]> alarmSampleBatch = new ArrayList<>();
    /** 관제 단위 → 센서 (IR 제외). 처음 쓸 때 읽음 */
    private Map<Long, java.util.Set<Long>> unitSensors;
    /** 센서 → 열려 있는 알림 id */
    private final Map<Long, java.util.Set<Long>> openAlarmsBySensor = new HashMap<>();
    private final Map<Long, Long> unitByAlarmId = new HashMap<>();
    private final Map<Long, Long> eventIds = new HashMap<>();
    private final Map<Long, Long> alarmIds = new HashMap<>();
    private final Map<Long, String> prefixes = new HashMap<>();

    private long trendCount;
    private long eventCount;
    private long alarmCount;

    public JdbcJudgementStore(JdbcTemplate jdbc, ZoneId zone, int batchSize) {
        this.jdbc = jdbc;
        this.zone = zone;
        this.batchSize = Math.max(1, batchSize);
    }

    @Override
    public void onReading(long sensorId, Instant ts, double value) {
        java.util.Set<Long> alarms = openAlarmsBySensor.get(sensorId);
        if (alarms == null || alarms.isEmpty()) {
            return;
        }
        for (Long alarmId : alarms) {
            alarmSampleBatch.add(new Object[] {alarmId, sensorId, odt(ts), value});
        }
        if (alarmSampleBatch.size() >= batchSize) {
            flushAlarmSamples();
        }
    }

    @Override
    public void onTrend(TrendPoint p) {
        trendBatch.add(new Object[] {p.sensorId(), odt(p.minute()), p.avg(), p.min(), p.max(), p.n()});
        trendCount++;
        if (trendBatch.size() >= batchSize) {
            flushTrends();
        }
    }

    @Override
    public void onEventOpened(long eventKey, long sensorId, long thresholdId, Stage stage,
                              Instant startedAt, Instant detectedAt, boolean lowSide, String rule,
                              List<Sample> backlog) {
        KeyHolder kh = new GeneratedKeyHolder();
        double peak = backlog.isEmpty() ? 0 : backlog.get(0).value();
        for (Sample s : backlog) {
            peak = lowSide ? Math.min(peak, s.value()) : Math.max(peak, s.value());
        }
        final double initialPeak = peak;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_EVENT, new String[] {"event_id"});
            ps.setLong(1, sensorId);
            ps.setLong(2, thresholdId);
            ps.setInt(3, stage.code());
            ps.setInt(4, stage.code());
            ps.setString(5, rule == null ? (lowSide ? "LO" : "HI") : rule);
            ps.setObject(6, odt(startedAt));
            ps.setDouble(7, initialPeak);
            ps.setString(8, edgeKey(sensorId, startedAt));
            return ps;
        }, kh);
        long eventId = kh.getKey().longValue();
        eventIds.put(eventKey, eventId);
        eventCount++;
        for (Sample s : backlog) {
            addSample(eventId, s);
        }
    }

    @Override
    public void onEventSample(long eventKey, Sample sample) {
        Long eventId = eventIds.get(eventKey);
        if (eventId != null) {
            addSample(eventId, sample);
        }
    }

    @Override
    public void onEventEscalated(long eventKey, Stage stage, Instant at) {
        Long eventId = eventIds.get(eventKey);
        if (eventId != null) {
            jdbc.update("UPDATE sensor_event SET stage = ?, max_stage = GREATEST(max_stage, ?) WHERE event_id = ?",
                    stage.code(), stage.code(), eventId);
        }
    }

    @Override
    public void onEventClosed(long eventKey, Instant endedAt, double peak) {
        Long eventId = eventIds.get(eventKey);
        if (eventId != null) {
            jdbc.update("UPDATE sensor_event SET ended_at = ?, peak_value = ? WHERE event_id = ?",
                    odt(endedAt), peak, eventId);
        }
    }

    @Override
    public void onAlarmOpened(long alarmKey, long unitId, Stage stage, Hazard hazard, Instant raisedAt) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO alarm (unit_id, stage, hazard, raised_at, status) VALUES (?, ?, ?, ?, 'OPEN')
                    """, new String[] {"alarm_id"});
            ps.setLong(1, unitId);
            ps.setInt(2, stage.code());
            ps.setString(3, hazard.name());
            ps.setObject(4, odt(raisedAt));
            return ps;
        }, kh);
        long alarmId = kh.getKey().longValue();
        alarmIds.put(alarmKey, alarmId);
        alarmCount++;
        unitByAlarmId.put(alarmId, unitId);
        for (Long sensorId : unitSensors().getOrDefault(unitId, java.util.Set.of())) {
            openAlarmsBySensor.computeIfAbsent(sensorId, k -> new java.util.HashSet<>()).add(alarmId);
        }
    }

    @Override
    public void onAlarmEventLinked(long alarmKey, long eventKey) {
        Long alarmId = alarmIds.get(alarmKey);
        Long eventId = eventIds.get(eventKey);
        if (alarmId != null && eventId != null) {
            jdbc.update("INSERT INTO alarm_event (alarm_id, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    alarmId, eventId);
            // 기준을 처음 넘은 때부터의 원인 센서 값 (이벤트 샘플) 을 알림 샘플로 옮긴다
            flushSamples();
            jdbc.update("""
                    INSERT INTO alarm_sensor_sample (alarm_id, sensor_id, ts, value)
                    SELECT ?, e.sensor_id, s.ts, s.value
                    FROM sensor_event e
                    JOIN sensor_event_sample s ON s.event_id = e.event_id
                    JOIN sensor se ON se.sensor_id = e.sensor_id AND se.sensor_type <> 'IR'
                    WHERE e.event_id = ?
                    ON CONFLICT DO NOTHING
                    """, alarmId, eventId);
        }
    }

    @Override
    public void onAlarmUpdated(long alarmKey, Stage stage, Hazard hazard, Instant at) {
        Long alarmId = alarmIds.get(alarmKey);
        if (alarmId != null) {
            jdbc.update("UPDATE alarm SET stage = ?, hazard = ? WHERE alarm_id = ?", stage.code(), hazard.name(), alarmId);
        }
    }

    @Override
    public void onAlarmCleared(long alarmKey, Instant at, boolean closed) {
        Long alarmId = alarmIds.get(alarmKey);
        if (alarmId != null && closed) {
            forgetAlarm(alarmId);
            // 과거 데이터는 사람이 조치하지 않고 값이 돌아와 닫힌 것으로 본다
            jdbc.update("UPDATE alarm SET status = 'CLOSED', closed_at = ?, close_reason = 'AUTO_RECOVERED' WHERE alarm_id = ?",
                    odt(at), alarmId);
        }
    }

    /** 모아 둔 batch 를 모두 넣는다 */
    public void flush() {
        flushTrends();
        flushSamples();
        flushAlarmSamples();
    }

    /** 사람이 이 관제 단위의 알림을 닫음 (실시간). 그 알림의 센서 값 저장을 멈춘다 */
    public void forgetUnit(long unitId) {
        flushAlarmSamples();
        unitByAlarmId.entrySet().removeIf(e -> {
            if (e.getValue() == unitId) {
                openAlarmsBySensor.values().forEach(set -> set.remove(e.getKey()));
                return true;
            }
            return false;
        });
    }

    private void forgetAlarm(long alarmId) {
        flushAlarmSamples();
        unitByAlarmId.remove(alarmId);
        openAlarmsBySensor.values().forEach(set -> set.remove(alarmId));
    }

    private Map<Long, java.util.Set<Long>> unitSensors() {
        if (unitSensors == null) {
            Map<Long, java.util.Set<Long>> m = new HashMap<>();
            jdbc.query("""
                    SELECT DISTINCT cus.unit_id, cus.sensor_id FROM control_unit_sensor cus
                    JOIN sensor s ON s.sensor_id = cus.sensor_id WHERE s.sensor_type <> 'IR'
                    """, rs -> {
                m.computeIfAbsent(rs.getLong("unit_id"), k -> new java.util.HashSet<>()).add(rs.getLong("sensor_id"));
            });
            unitSensors = m;
        }
        return unitSensors;
    }

    private void flushAlarmSamples() {
        if (!alarmSampleBatch.isEmpty()) {
            jdbc.batchUpdate(INSERT_ALARM_SAMPLE, alarmSampleBatch);
            alarmSampleBatch.clear();
        }
    }

    /** 엔진 번호 → DB event_id (없으면 null) */
    public Long eventId(long eventKey) {
        return eventIds.get(eventKey);
    }

    /** 엔진 번호 → DB alarm_id (없으면 null) */
    public Long alarmId(long alarmKey) {
        return alarmIds.get(alarmKey);
    }

    public long trendCount() {
        return trendCount;
    }

    public long eventCount() {
        return eventCount;
    }

    public long alarmCount() {
        return alarmCount;
    }

    private void addSample(long eventId, Sample s) {
        sampleBatch.add(new Object[] {eventId, odt(s.ts()), s.value()});
        if (sampleBatch.size() >= batchSize) {
            flushSamples();
        }
    }

    private void flushTrends() {
        if (!trendBatch.isEmpty()) {
            jdbc.batchUpdate(INSERT_TREND, trendBatch);
            trendBatch.clear();
        }
    }

    private void flushSamples() {
        if (!sampleBatch.isEmpty()) {
            jdbc.batchUpdate(INSERT_SAMPLE, sampleBatch);
            sampleBatch.clear();
        }
    }

    /**
     * 중복 방지 키 = 공장 코드 + 태그 + 시작 시각 (예: P5-AT-301-20261117T175525). v5 의 엣지 키와 같은 모양.
     * 같은 키가 이미 있으면 뒤에 번호를 붙인다.
     */
    private String edgeKey(long sensorId, Instant startedAt) {
        String prefix = prefixes.computeIfAbsent(sensorId, id -> {
            List<String> rows = jdbc.queryForList("""
                    SELECT f.code || '-' || s.tag FROM sensor s
                    JOIN equipment e ON e.equipment_id = s.equipment_id
                    JOIN process p ON p.process_id = e.process_id
                    JOIN factory f ON f.factory_id = p.factory_id
                    WHERE s.sensor_id = ?
                    """, String.class, id);
            return rows.isEmpty() ? "S" + id : rows.get(0);
        });
        String key = edgeKey(prefix, startedAt, zone);
        String candidate = key;
        for (int n = 2; exists(candidate); n++) {
            candidate = key + "-" + n;
        }
        return candidate;
    }

    static String edgeKey(String prefix, Instant startedAt, ZoneId zone) {
        String key = prefix + "-" + EDGE_TS.format(startedAt.atZone(zone));
        return key.length() > 36 ? key.substring(0, 36) : key;
    }

    private boolean exists(String edgeKey) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM sensor_event WHERE edge_key = ?", Integer.class, edgeKey);
        return n != null && n > 0;
    }

    private OffsetDateTime odt(Instant i) {
        return i.atZone(zone).toOffsetDateTime();
    }
}
