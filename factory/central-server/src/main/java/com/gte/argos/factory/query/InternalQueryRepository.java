package com.gte.argos.factory.query;

import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.common.time.Seasons;
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
        return jdbc.query("""
                SELECT e.*, COALESCE(e.fan_auto, (
                           SELECT bool_and(f.fan_auto) FROM control_unit cu
                           JOIN control_unit_sensor cus ON cus.unit_id = cu.unit_id
                           JOIN sensor s ON s.sensor_id = cus.sensor_id
                           JOIN equipment f ON f.equipment_id = s.equipment_id AND f.equipment_type = 'FAN'
                           WHERE cu.equipment_id = e.equipment_id)) AS fan_auto_view
                FROM equipment e ORDER BY e.process_id, e.equipment_id
                """, (rs, i) -> new Equipment(
                rs.getLong("equipment_id"), rs.getLong("process_id"), rs.getString("code"), rs.getString("name"),
                rs.getString("equipment_type"), rs.getString("shape"),
                rs.getObject("pos_x", Double.class), rs.getObject("pos_y", Double.class), rs.getObject("pos_z", Double.class),
                rs.getObject("size_w", Double.class), rs.getObject("size_d", Double.class), rs.getObject("size_h", Double.class),
                rs.getBoolean("is_active"), rs.getString("run_state"), rs.getObject("fan_rpm", Integer.class),
                rs.getObject("fan_auto_view", Boolean.class)));
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
                    rs.getInt("sample_sec"), rs.getObject("setpoint", Double.class), rs.getBoolean("is_active"), th,
                    rs.getString("stream_url"));
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
        // 센서마다 기본키 인덱스(sensor_id, ts)로 마지막 한 줄만 읽는다. 전체를 정렬하는 DISTINCT ON 은
        // 30일치(약 60만 행)에서 0.6초 걸려 화면 상태 API 가 느렸다 (2026-11-20). 끊김만 있던 분(avg_v null)은 건너뜀
        jdbc.query("""
                SELECT s.sensor_id, t.ts, t.avg_v FROM sensor s
                CROSS JOIN LATERAL (
                    SELECT ts, avg_v FROM sensor_trend t
                    WHERE t.sensor_id = s.sensor_id AND t.avg_v IS NOT NULL
                    ORDER BY ts DESC LIMIT 1) t
                """,
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
        if (f.hazard() != null && !f.hazard().isBlank()) {
            where.append(" AND a.hazard = :hazard");
            p.addValue("hazard", f.hazard());
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
        Map<Long, InternalDto.FanAuto> fans = fanAuto(rows);
        List<Alarm> out = new ArrayList<>(rows.size());
        for (AlarmRow r : rows) {
            List<Event> events = byAlarm.getOrDefault(r.alarmId(), List.of());
            boolean stillOver = events.stream().anyMatch(e -> e.endedAt() == null);
            out.add(new Alarm(r.alarmId(), r.unitId(), r.unitName(), r.processId(), r.equipmentId(), r.factoryId(),
                    r.plantId(), r.stage(), r.hazard(), r.status(), r.raisedAt(), r.ackedBy(), r.ackedAt(),
                    r.closedBy(), r.closedAt(), r.thermalFileId(), r.closeReason(), stillOver, events,
                    fans.get(r.alarmId())));
        }
        return out;
    }

    /** 가스 알림의 배기팬 자동 제어 결과. 그 알림의 가장 최근 AUTO 명령, 없으면 관제 단위 배기팬 스위치 값 */
    private Map<Long, InternalDto.FanAuto> fanAuto(List<AlarmRow> rows) {
        List<AlarmRow> gas = rows.stream().filter(r -> "GAS".equals(r.hazard())).toList();
        Map<Long, InternalDto.FanAuto> byCommand = new HashMap<>();
        if (gas.isEmpty()) {
            return byCommand;
        }
        jdbc.query("""
                SELECT DISTINCT ON (c.alarm_id) c.alarm_id, c.command_id, c.equipment_id, e.name, c.value, c.result,
                       c.requested_at, c.approved_by, (c.result_detail ->> 'beforeRpm')::int AS before_rpm
                FROM equipment_command c JOIN equipment e ON e.equipment_id = c.equipment_id
                WHERE c.alarm_id IN (:ids) AND c.source = 'AUTO' AND c.command = 'FAN_HZ'
                ORDER BY c.alarm_id, c.requested_at DESC, c.command_id DESC
                """, new MapSqlParameterSource("ids", gas.stream().map(AlarmRow::alarmId).toList()), rs -> {
            int hz = (int) Math.round(rs.getDouble("value"));
            String result = rs.getString("result");
            // 승인 대기 · 거절 · 승인해서 실행한 명령은 수동 팬
            boolean manual = "AWAITING".equals(result) || "DENIED".equals(result) || rs.getObject("approved_by") != null;
            byCommand.put(rs.getLong("alarm_id"), new InternalDto.FanAuto(null, rs.getLong("equipment_id"),
                    rs.getString("name"), manual ? "MANUAL" : "AUTO", Math.round(hz * 100f / 60), hz * 30,
                    rs.getObject("before_rpm", Integer.class), fanStatus(result),
                    odt(rs, "requested_at"), rs.getLong("command_id")));
        });
        List<Long> units = gas.stream().filter(r -> !byCommand.containsKey(r.alarmId()))
                .map(AlarmRow::unitId).distinct().toList();
        Map<Long, Object[]> unitFan = new HashMap<>();
        if (!units.isEmpty()) {
            jdbc.query("""
                    SELECT DISTINCT ON (cus.unit_id) cus.unit_id, f.equipment_id, f.name, f.fan_auto
                    FROM control_unit_sensor cus
                    JOIN sensor s ON s.sensor_id = cus.sensor_id
                    JOIN equipment f ON f.equipment_id = s.equipment_id AND f.equipment_type = 'FAN'
                    WHERE cus.unit_id IN (:units)
                    ORDER BY cus.unit_id, f.equipment_id
                    """, new MapSqlParameterSource("units", units), rs -> {
                unitFan.put(rs.getLong("unit_id"), new Object[] {rs.getLong("equipment_id"), rs.getString("name"),
                        rs.getObject("fan_auto", Boolean.class)});
            });
        }
        Map<Long, InternalDto.FanAuto> result = new HashMap<>();
        for (AlarmRow r : gas) {
            InternalDto.FanAuto f = byCommand.get(r.alarmId());
            if (f != null) {
                result.put(r.alarmId(), new InternalDto.FanAuto(r.equipmentId(), f.fanEquipmentId(), f.fanName(),
                        f.mode(), f.percent(), f.rpm(), f.beforeRpm(), f.status(), f.at(), f.commandId()));
                continue;
            }
            Object[] fan = unitFan.get(r.unitId());
            if (fan != null) {
                String mode = Boolean.FALSE.equals(fan[2]) ? "MANUAL" : "AUTO";
                result.put(r.alarmId(), new InternalDto.FanAuto(r.equipmentId(), (Long) fan[0], (String) fan[1], mode,
                        null, null, null, "SKIPPED", null, null));
            }
        }
        return result;
    }

    /** 알림 상세 그래프 = 그 관제 단위 센서 전부(IR 제외) */
    public InternalDto.AlarmSeries alarmSeries(Alarm alarm) {
        OffsetDateTime from = alarm.events().stream().map(Event::startedAt).filter(java.util.Objects::nonNull)
                .min(OffsetDateTime::compareTo).orElse(alarm.raisedAt());
        OffsetDateTime to = alarm.closedAt() != null ? alarm.closedAt() : clock.nowOffset();
        if (to.isBefore(from)) {
            to = from;
        }
        int interval = intervalSec(from, to);
        java.util.Set<Long> triggers = new java.util.HashSet<>();
        alarm.events().forEach(e -> triggers.add(e.sensorId()));
        MapSqlParameterSource p = new MapSqlParameterSource("unitId", alarm.unitId())
                .addValue("raisedAt", alarm.raisedAt()).addValue("season", Seasons.current(clock))
                .addValue("alarmId", alarm.alarmId()).addValue("from", from).addValue("to", to);
        List<Object[]> meta = new ArrayList<>();
        jdbc.query("""
                SELECT DISTINCT s.sensor_id, s.tag, s.sensor_type, s.unit,
                       t.warn_value, t.alarm_value, t.low_warn, t.low_alarm
                FROM control_unit_sensor cus
                JOIN sensor s ON s.sensor_id = cus.sensor_id AND s.sensor_type <> 'IR'
                LEFT JOIN LATERAL (
                    SELECT * FROM sensor_threshold th
                    WHERE th.sensor_id = s.sensor_id AND th.valid_from <= :raisedAt
                      AND (th.valid_to IS NULL OR th.valid_to > :raisedAt)
                    ORDER BY (th.season = :season) DESC, (th.season = 'ALL') DESC, th.valid_from DESC
                    LIMIT 1
                ) t ON TRUE
                WHERE cus.unit_id = :unitId
                ORDER BY s.sensor_id
                """, p, rs -> {
            meta.add(new Object[] {rs.getLong("sensor_id"), rs.getString("tag"), rs.getString("sensor_type"),
                    rs.getString("unit"), rs.getObject("warn_value", Double.class), rs.getObject("alarm_value", Double.class),
                    rs.getObject("low_warn", Double.class), rs.getObject("low_alarm", Double.class)});
        });
        Map<Long, List<Object[]>> points = new HashMap<>();
        String sql = interval == 5
                ? "SELECT sensor_id, ts, value AS v FROM alarm_sensor_sample WHERE alarm_id = :alarmId "
                  + "AND ts >= :from AND ts <= :to ORDER BY sensor_id, ts"
                : "SELECT sensor_id, ts, avg_v AS v FROM sensor_trend WHERE sensor_id IN (:ids) "
                  + "AND ts >= date_trunc('minute', CAST(:from AS timestamptz)) AND ts <= :to ORDER BY sensor_id, ts";
        if (!meta.isEmpty()) {
            p.addValue("ids", meta.stream().map(m -> (Long) m[0]).toList());
            jdbc.query(sql, p, rs -> {
                Double v = rs.getObject("v", Double.class);
                if (v != null) {
                    points.computeIfAbsent(rs.getLong("sensor_id"), k -> new ArrayList<>())
                            .add(new Object[] {odt(rs, "ts"), v});
                }
            });
        }
        List<InternalDto.AlarmSensorSeries> sensors = new ArrayList<>();
        for (Object[] m : meta) {
            long id = (Long) m[0];
            sensors.add(new InternalDto.AlarmSensorSeries(id, (String) m[1], (String) m[2], (String) m[3],
                    (Double) m[4], (Double) m[5], (Double) m[6], (Double) m[7], triggers.contains(id),
                    points.getOrDefault(id, List.of())));
        }
        return new InternalDto.AlarmSeries(from.atZoneSameInstant(clock.zone()).toOffsetDateTime(),
                to.atZoneSameInstant(clock.zone()).toOffsetDateTime(), interval, sensors);
    }

    /** 30분 이하면 5초, 넘으면 60초(1분 평균) */
    static int intervalSec(OffsetDateTime from, OffsetDateTime to) {
        return java.time.Duration.between(from, to).compareTo(java.time.Duration.ofMinutes(30)) <= 0 ? 5 : 60;
    }

    /** equipment_command.result → fanAuto.status */
    static String fanStatus(String result) {
        return switch (result) {
            case "PENDING" -> "SENT";
            case "AWAITING" -> "AWAITING";
            case "DENIED" -> "DENIED";
            case "DONE", "DUPLICATE" -> "APPLIED";
            default -> "FAILED";
        };
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
                              Long processId, Long factoryId, Long plantId, int page, int size, String hazard) {
        public AlarmFilter(Instant from, Instant to, List<String> statuses, Integer minStage, Long unitId,
                           Long processId, Long factoryId, Long plantId, int page, int size) {
            this(from, to, statuses, minStage, unitId, processId, factoryId, plantId, page, size, null);
        }
    }
}
