package com.gte.argos.factory.query;

import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.query.InternalDto.Alarm;
import com.gte.argos.factory.query.InternalDto.Equipment;
import com.gte.argos.factory.query.InternalDto.Event;
import com.gte.argos.factory.query.InternalDto.Factory;
import com.gte.argos.factory.query.InternalDto.Plant;
import com.gte.argos.factory.query.InternalDto.Process;
import com.gte.argos.factory.query.InternalDto.Sample;
import com.gte.argos.factory.query.InternalDto.Sensor;
import com.gte.argos.factory.query.InternalDto.Threshold;
import com.gte.argos.factory.query.InternalDto.Unit;
import com.gte.argos.factory.query.InternalDto.UnitMember;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 연계 서버용 조회 SQL 모음. 읽기만 한다 (경보 상태 변경은 AlarmStatusService) */
@Repository
public class InternalQueryRepository {

    private static final String THRESHOLD_LATERAL = """
            LEFT JOIN LATERAL (
                SELECT * FROM sensor_threshold th
                WHERE th.sensor_id = s.sensor_id AND th.valid_to IS NULL
                ORDER BY (th.season = :season) DESC, (th.season = 'ALL') DESC, th.valid_from DESC
                LIMIT 1
            ) t ON TRUE
            """;

    private static final String EVENT_SELECT = """
            SELECT ev.event_id, ev.sensor_id, s.tag, s.sensor_type, s.unit, s.equipment_id, e.process_id,
                   ev.stage, ev.rule, ev.started_at, ev.ended_at, ev.peak_value, ev.threshold_id,
                   ev.max_stage, ev.edge_key, th.warn_value, th.alarm_value, th.low_warn, th.low_alarm
            FROM sensor_event ev
            JOIN sensor s ON s.sensor_id = ev.sensor_id
            JOIN equipment e ON e.equipment_id = s.equipment_id
            LEFT JOIN sensor_threshold th ON th.threshold_id = ev.threshold_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final DemoClock clock;

    public InternalQueryRepository(NamedParameterJdbcTemplate jdbc, DemoClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ---------------------------------------------------------------
    // 마스터
    // ---------------------------------------------------------------

    public List<Plant> plants() {
        return jdbc.query("SELECT * FROM plant ORDER BY plant_id", (rs, i) -> new Plant(
                rs.getLong("plant_id"), rs.getString("code"), rs.getString("name"), rs.getString("address"),
                rs.getObject("lat", Double.class), rs.getObject("lng", Double.class)));
    }

    public List<Factory> factories() {
        return jdbc.query("SELECT * FROM factory ORDER BY plant_id, sort_order, factory_id", (rs, i) -> new Factory(
                rs.getLong("factory_id"), rs.getLong("plant_id"), rs.getString("code"), rs.getString("name"),
                rs.getInt("sort_order"), rs.getString("map_shape")));
    }

    public List<Process> processes() {
        return jdbc.query("SELECT * FROM process ORDER BY factory_id, process_id", (rs, i) -> new Process(
                rs.getLong("process_id"), rs.getLong("factory_id"), rs.getString("code"), rs.getString("name"),
                rs.getString("tag_prefix"), rs.getObject("floorplan_file_id", Long.class)));
    }

    public List<Equipment> equipment() {
        return jdbc.query("SELECT * FROM equipment ORDER BY process_id, equipment_id", (rs, i) -> new Equipment(
                rs.getLong("equipment_id"), rs.getLong("process_id"), rs.getString("code"), rs.getString("name"),
                rs.getString("equipment_type"), rs.getString("shape"),
                rs.getObject("pos_x", Double.class), rs.getObject("pos_y", Double.class), rs.getObject("pos_z", Double.class),
                rs.getObject("size_w", Double.class), rs.getObject("size_d", Double.class), rs.getObject("size_h", Double.class),
                rs.getBoolean("is_active"), rs.getString("run_state"), rs.getObject("fan_rpm", Integer.class)));
    }

    public List<Sensor> sensors() {
        return jdbc.query("SELECT s.*, t.threshold_id, t.warn_value, t.alarm_value, t.low_warn, t.low_alarm, "
                + "t.warn_samples, t.alarm_samples, t.season, t.basis, t.note, t.valid_from "
                + "FROM sensor s " + THRESHOLD_LATERAL + " ORDER BY s.sensor_id", season(), (rs, i) -> {
            Long thresholdId = rs.getObject("threshold_id", Long.class);
            Threshold th = thresholdId == null ? null : new Threshold(thresholdId,
                    rs.getObject("warn_value", Double.class), rs.getObject("alarm_value", Double.class),
                    rs.getObject("low_warn", Double.class), rs.getObject("low_alarm", Double.class),
                    rs.getInt("warn_samples"), rs.getInt("alarm_samples"), rs.getString("season"),
                    rs.getString("basis"), rs.getString("note"), odt(rs, "valid_from"));
            return new Sensor(rs.getLong("sensor_id"), rs.getString("tag"), rs.getLong("equipment_id"),
                    rs.getString("sensor_type"), rs.getString("unit"), rs.getString("connection"),
                    rs.getInt("sample_sec"), rs.getObject("setpoint", Double.class), rs.getBoolean("is_active"), th);
        });
    }

    public List<Unit> units() {
        Map<Long, List<UnitMember>> members = new HashMap<>();
        jdbc.query("SELECT unit_id, sensor_id, hazard FROM control_unit_sensor ORDER BY unit_id, sensor_id, hazard",
                rs -> {
                    members.computeIfAbsent(rs.getLong("unit_id"), k -> new ArrayList<>())
                            .add(new UnitMember(rs.getLong("sensor_id"), rs.getString("hazard")));
                });
        return jdbc.query("SELECT * FROM control_unit ORDER BY unit_id", (rs, i) -> new Unit(
                rs.getLong("unit_id"), rs.getLong("process_id"), rs.getObject("equipment_id", Long.class),
                rs.getString("name"), rs.getBoolean("is_active"),
                members.getOrDefault(rs.getLong("unit_id"), List.of())));
    }

    // ---------------------------------------------------------------
    // 현재 상태용
    // ---------------------------------------------------------------

    /** 센서 + 소속 계층 + 지금 기준값 */
    public List<SensorRow> sensorRows() {
        return jdbc.query("""
                SELECT s.sensor_id, s.tag, s.sensor_type, s.unit, s.equipment_id,
                       e.process_id, p.factory_id, f.plant_id,
                       t.warn_value, t.alarm_value, t.low_warn, t.low_alarm
                FROM sensor s
                JOIN equipment e ON e.equipment_id = s.equipment_id
                JOIN process p ON p.process_id = e.process_id
                JOIN factory f ON f.factory_id = p.factory_id
                """ + THRESHOLD_LATERAL + """
                WHERE s.is_active
                ORDER BY s.sensor_id
                """, season(), (rs, i) -> new SensorRow(rs.getLong("sensor_id"), rs.getString("tag"), rs.getString("sensor_type"),
                rs.getString("unit"), rs.getLong("equipment_id"), rs.getLong("process_id"), rs.getLong("factory_id"),
                rs.getLong("plant_id"), rs.getObject("warn_value", Double.class), rs.getObject("alarm_value", Double.class),
                rs.getObject("low_warn", Double.class), rs.getObject("low_alarm", Double.class)));
    }

    /** 진행 중 이벤트(ended_at IS NULL)의 센서별 최고 단계 */
    public Map<Long, Integer> openStages() {
        Map<Long, Integer> out = new HashMap<>();
        jdbc.query("SELECT sensor_id, MAX(stage) AS stage FROM sensor_event WHERE ended_at IS NULL GROUP BY sensor_id",
                rs -> {
                    out.put(rs.getLong("sensor_id"), rs.getInt("stage"));
                });
        return out;
    }

    /** 센서별 마지막 1분 추이 (실시간 값이 아직 없을 때 쓴다) */
    public Map<Long, TrendValue> lastTrends() {
        Map<Long, TrendValue> out = new HashMap<>();
        jdbc.query("SELECT DISTINCT ON (sensor_id) sensor_id, ts, avg_v FROM sensor_trend ORDER BY sensor_id, ts DESC",
                rs -> {
                    out.put(rs.getLong("sensor_id"), new TrendValue(odt(rs, "ts"), rs.getDouble("avg_v")));
                });
        return out;
    }

    /** 단위 → 구성 센서 */
    public Map<Long, List<Long>> unitSensors() {
        Map<Long, List<Long>> out = new HashMap<>();
        jdbc.query("SELECT DISTINCT cus.unit_id, cus.sensor_id FROM control_unit_sensor cus "
                + "JOIN control_unit cu ON cu.unit_id = cus.unit_id WHERE cu.is_active", rs -> {
            out.computeIfAbsent(rs.getLong("unit_id"), k -> new ArrayList<>()).add(rs.getLong("sensor_id"));
        });
        return out;
    }

    public List<EquipmentRow> equipmentRows() {
        return jdbc.query("""
                SELECT e.equipment_id, e.process_id, p.factory_id, f.plant_id, e.run_state, e.fan_rpm
                FROM equipment e
                JOIN process p ON p.process_id = e.process_id
                JOIN factory f ON f.factory_id = p.factory_id
                WHERE e.is_active
                ORDER BY e.equipment_id
                """, (rs, i) -> new EquipmentRow(rs.getLong("equipment_id"), rs.getLong("process_id"),
                rs.getLong("factory_id"), rs.getLong("plant_id"), rs.getString("run_state"),
                rs.getObject("fan_rpm", Integer.class)));
    }

    // ---------------------------------------------------------------
    // 추이
    // ---------------------------------------------------------------

    /** 1분 추이: sensorId → [시각, 평균, 최소, 최대] */
    public Map<Long, List<Object[]>> trend1m(Collection<Long> sensorIds, Instant from, Instant to) {
        Map<Long, List<Object[]>> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT sensor_id, ts, avg_v, min_v, max_v FROM sensor_trend
                WHERE sensor_id IN (:ids) AND ts >= :from AND ts < :to
                ORDER BY sensor_id, ts
                """, new MapSqlParameterSource().addValue("ids", sensorIds)
                .addValue("from", odt(from)).addValue("to", odt(to)), rs -> {
            out.computeIfAbsent(rs.getLong("sensor_id"), k -> new ArrayList<>())
                    .add(new Object[] {odt(rs, "ts"), rs.getDouble("avg_v"), rs.getDouble("min_v"), rs.getDouble("max_v")});
        });
        return out;
    }

    /** 10분 묶음 추이 */
    public Map<Long, List<Object[]>> trend10m(Collection<Long> sensorIds, Instant from, Instant to) {
        Map<Long, List<Object[]>> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT sensor_id, date_bin(INTERVAL '10 minutes', ts, TIMESTAMPTZ '2000-01-01 00:00:00+00') AS b,
                       ROUND(AVG(avg_v)::numeric, 3)::float8 AS avg_v, MIN(min_v) AS min_v, MAX(max_v) AS max_v
                FROM sensor_trend
                WHERE sensor_id IN (:ids) AND ts >= :from AND ts < :to
                GROUP BY sensor_id, b
                ORDER BY sensor_id, b
                """, new MapSqlParameterSource().addValue("ids", sensorIds)
                .addValue("from", odt(from)).addValue("to", odt(to)), rs -> {
            out.computeIfAbsent(rs.getLong("sensor_id"), k -> new ArrayList<>())
                    .add(new Object[] {odt(rs, "b"), rs.getDouble("avg_v"), rs.getDouble("min_v"), rs.getDouble("max_v")});
        });
        return out;
    }

    // ---------------------------------------------------------------
    // 이벤트
    // ---------------------------------------------------------------

    public List<Event> events(EventFilter f) {
        StringBuilder sql = new StringBuilder(EVENT_SELECT)
                .append(" WHERE ev.started_at < :to AND (ev.ended_at IS NULL OR ev.ended_at >= :from)");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", odt(f.from())).addValue("to", odt(f.to())).addValue("limit", f.limit());
        if (f.sensorId() != null) {
            sql.append(" AND ev.sensor_id = :sensorId");
            p.addValue("sensorId", f.sensorId());
        }
        if (f.equipmentId() != null) {
            sql.append(" AND s.equipment_id = :equipmentId");
            p.addValue("equipmentId", f.equipmentId());
        }
        if (f.processId() != null) {
            sql.append(" AND e.process_id = :processId");
            p.addValue("processId", f.processId());
        }
        if (f.openOnly()) {
            sql.append(" AND ev.ended_at IS NULL");
        }
        sql.append(" ORDER BY ev.started_at DESC LIMIT :limit");
        return jdbc.query(sql.toString(), p, (rs, i) -> event(rs));
    }

    public List<Sample> samples(long eventId) {
        return jdbc.query("SELECT ts, value FROM sensor_event_sample WHERE event_id = :id ORDER BY ts",
                new MapSqlParameterSource("id", eventId),
                (rs, i) -> new Sample(odt(rs, "ts"), rs.getObject("value", Double.class)));
    }

    // ---------------------------------------------------------------
    // 경보
    // ---------------------------------------------------------------

    private static final String ALARM_FROM = """
            FROM alarm a
            JOIN control_unit cu ON cu.unit_id = a.unit_id
            JOIN process p ON p.process_id = cu.process_id
            JOIN factory f ON f.factory_id = p.factory_id
            """;

    public InternalDto.Page<Alarm> alarms(AlarmFilter f) {
        StringBuilder where = new StringBuilder(" WHERE a.raised_at >= :from AND a.raised_at < :to");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", odt(f.from())).addValue("to", odt(f.to()))
                .addValue("limit", f.size()).addValue("offset", (long) f.page() * f.size());
        if (f.statuses() != null && !f.statuses().isEmpty()) {
            where.append(" AND a.status IN (:statuses)");
            p.addValue("statuses", f.statuses());
        }
        if (f.minStage() != null) {
            where.append(" AND a.stage >= :minStage");
            p.addValue("minStage", f.minStage());
        }
        if (f.unitId() != null) {
            where.append(" AND a.unit_id = :unitId");
            p.addValue("unitId", f.unitId());
        }
        if (f.processId() != null) {
            where.append(" AND cu.process_id = :processId");
            p.addValue("processId", f.processId());
        }
        if (f.factoryId() != null) {
            where.append(" AND p.factory_id = :factoryId");
            p.addValue("factoryId", f.factoryId());
        }
        if (f.plantId() != null) {
            where.append(" AND f.plant_id = :plantId");
            p.addValue("plantId", f.plantId());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + ALARM_FROM + where, p, Long.class);
        List<AlarmRow> rows = jdbc.query("SELECT a.*, cu.name AS unit_name, cu.process_id, cu.equipment_id AS unit_equipment_id, "
                + "p.factory_id, f.plant_id " + ALARM_FROM + where
                + " ORDER BY a.raised_at DESC, a.alarm_id DESC LIMIT :limit OFFSET :offset", p, (rs, i) -> alarmRow(rs));
        return new InternalDto.Page<>(withEvents(rows), f.page(), f.size(), total == null ? 0 : total);
    }

    public Alarm alarm(long alarmId) {
        List<AlarmRow> rows = jdbc.query("SELECT a.*, cu.name AS unit_name, cu.process_id, cu.equipment_id AS unit_equipment_id, "
                + "p.factory_id, f.plant_id " + ALARM_FROM + " WHERE a.alarm_id = :id",
                new MapSqlParameterSource("id", alarmId), (rs, i) -> alarmRow(rs));
        if (rows.isEmpty()) {
            return null;
        }
        return withEvents(rows).get(0);
    }

    private List<Alarm> withEvents(List<AlarmRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Event>> byAlarm = new HashMap<>();
        List<Long> ids = rows.stream().map(AlarmRow::alarmId).toList();
        jdbc.query("SELECT ae.alarm_id, x.* FROM alarm_event ae JOIN (" + EVENT_SELECT + ") x ON x.event_id = ae.event_id "
                + "WHERE ae.alarm_id IN (:ids) ORDER BY x.started_at",
                new MapSqlParameterSource("ids", ids), rs -> {
                    byAlarm.computeIfAbsent(rs.getLong("alarm_id"), k -> new ArrayList<>()).add(event(rs));
                });
        List<Alarm> out = new ArrayList<>(rows.size());
        for (AlarmRow r : rows) {
            List<Event> events = byAlarm.getOrDefault(r.alarmId(), List.of());
            boolean stillOver = events.stream().anyMatch(e -> e.endedAt() == null);
            out.add(new Alarm(r.alarmId(), r.unitId(), r.unitName(), r.processId(), r.equipmentId(), r.factoryId(),
                    r.plantId(), r.stage(), r.hazard(), r.status(), r.raisedAt(), r.ackedBy(), r.ackedAt(),
                    r.closedBy(), r.closedAt(), r.thermalFileId(), r.closeReason(), stillOver, events));
        }
        return out;
    }

    // ---------------------------------------------------------------
    // 공통
    // ---------------------------------------------------------------

    private MapSqlParameterSource season() {
        return new MapSqlParameterSource("season", com.gte.argos.factory.common.time.Seasons.current(clock));
    }

    private Event event(ResultSet rs) throws SQLException {
        return new Event(rs.getLong("event_id"), rs.getLong("sensor_id"), rs.getString("tag"),
                rs.getString("sensor_type"), rs.getString("unit"), rs.getLong("equipment_id"), rs.getLong("process_id"),
                rs.getInt("stage"), rs.getString("rule"), odt(rs, "started_at"), odt(rs, "ended_at"),
                rs.getObject("peak_value", Double.class), rs.getObject("threshold_id", Long.class),
                rs.getObject("warn_value", Double.class), rs.getObject("alarm_value", Double.class),
                rs.getObject("low_warn", Double.class), rs.getObject("low_alarm", Double.class),
                rs.getInt("max_stage"), rs.getString("edge_key"));
    }

    private AlarmRow alarmRow(ResultSet rs) throws SQLException {
        return new AlarmRow(rs.getLong("alarm_id"), rs.getLong("unit_id"), rs.getString("unit_name"),
                rs.getLong("process_id"), rs.getObject("unit_equipment_id", Long.class), rs.getLong("factory_id"),
                rs.getLong("plant_id"), rs.getInt("stage"), rs.getString("hazard"), rs.getString("status"),
                odt(rs, "raised_at"), rs.getObject("acked_by", Long.class), odt(rs, "acked_at"),
                rs.getObject("closed_by", Long.class), odt(rs, "closed_at"), rs.getObject("thermal_file", Long.class),
                rs.getString("close_reason"));
    }

    private OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.atZoneSameInstant(clock.zone()).toOffsetDateTime();
    }

    private OffsetDateTime odt(Instant i) {
        return i.atZone(clock.zone()).toOffsetDateTime();
    }

    // ---------------------------------------------------------------
    // 내부 행 · 조건
    // ---------------------------------------------------------------

    public record SensorRow(long sensorId, String tag, String type, String unit, long equipmentId, long processId,
                            long factoryId, long plantId, Double warn, Double alarm, Double lowWarn, Double lowAlarm) {
    }

    public record EquipmentRow(long equipmentId, long processId, long factoryId, long plantId, String runState,
                               Integer fanRpm) {
    }

    public record TrendValue(OffsetDateTime ts, double avg) {
    }

    private record AlarmRow(long alarmId, long unitId, String unitName, long processId, Long equipmentId,
                            long factoryId, long plantId, int stage, String hazard, String status,
                            OffsetDateTime raisedAt, Long ackedBy, OffsetDateTime ackedAt,
                            Long closedBy, OffsetDateTime closedAt, Long thermalFileId, String closeReason) {
    }

    public record EventFilter(Instant from, Instant to, Long sensorId, Long equipmentId, Long processId,
                              boolean openOnly, int limit) {
    }

    public record AlarmFilter(Instant from, Instant to, List<String> statuses, Integer minStage, Long unitId,
                              Long processId, Long factoryId, Long plantId, int page, int size) {
    }
}
