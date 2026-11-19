package com.gte.argos.factory.master;

import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.common.time.Seasons;
import com.gte.argos.factory.judge.Hazard;
import com.gte.argos.factory.judge.SensorDef;
import com.gte.argos.factory.judge.ThresholdRule;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB 의 센서·기준값·관제 단위 정보를 판정 엔진용 SensorDef 로 읽는다.
 * 기준값은 지금 적용 중인 줄(valid_to IS NULL)을 쓰고, 지금 계절 줄이 있으면 그것을, 없으면 ALL 을 쓴다.
 */
@Component
public class SensorDefinitionLoader {

    private final JdbcTemplate jdbc;
    private final DemoClock clock;

    public SensorDefinitionLoader(JdbcTemplate jdbc, DemoClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<SensorDef> loadActive() {
        Map<Long, Map<Long, Set<Hazard>>> membership = new HashMap<>();
        jdbc.query("""
                SELECT cus.sensor_id, cus.unit_id, cus.hazard
                FROM control_unit_sensor cus
                JOIN control_unit cu ON cu.unit_id = cus.unit_id
                WHERE cu.is_active
                ORDER BY cus.unit_id
                """, rs -> {
            membership
                    .computeIfAbsent(rs.getLong("sensor_id"), k -> new LinkedHashMap<>())
                    .computeIfAbsent(rs.getLong("unit_id"), k -> EnumSet.noneOf(Hazard.class))
                    .add(Hazard.valueOf(rs.getString("hazard")));
        });

        return jdbc.query("""
                SELECT s.sensor_id, s.tag, s.sensor_type, s.sample_sec,
                       t.threshold_id, t.warn_value, t.alarm_value, t.low_warn, t.low_alarm,
                       t.warn_samples, t.alarm_samples, t.release_samples, t.release_margin, t.immediate_value
                FROM sensor s
                LEFT JOIN LATERAL (
                    SELECT *
                    FROM sensor_threshold th
                    WHERE th.sensor_id = s.sensor_id AND th.valid_to IS NULL
                    ORDER BY (th.season = ?) DESC, (th.season = 'ALL') DESC, th.valid_from DESC
                    LIMIT 1
                ) t ON TRUE
                WHERE s.is_active
                ORDER BY s.sensor_id
                """, (rs, i) -> {
            long sensorId = rs.getLong("sensor_id");
            ThresholdRule rule = null;
            long thresholdId = rs.getLong("threshold_id");
            if (!rs.wasNull()) {
                rule = new ThresholdRule(
                        thresholdId,
                        (Double) rs.getObject("warn_value"),
                        (Double) rs.getObject("alarm_value"),
                        (Double) rs.getObject("low_warn"),
                        (Double) rs.getObject("low_alarm"),
                        rs.getInt("warn_samples"),
                        rs.getInt("alarm_samples"),
                        rs.getInt("release_samples"),
                        rs.getDouble("release_margin"),
                        (Double) rs.getObject("immediate_value"));
            }
            List<SensorDef.UnitMembership> units = new ArrayList<>();
            membership.getOrDefault(sensorId, Map.of())
                    .forEach((unitId, hazards) -> units.add(new SensorDef.UnitMembership(unitId, hazards)));
            return new SensorDef(sensorId, rs.getString("tag"), rs.getString("sensor_type"),
                    rs.getInt("sample_sec"), rule, units);
        }, Seasons.current(clock));
    }
}
