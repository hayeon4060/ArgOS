package com.gte.argos.gateway.fire;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 알림 → 화재 id (fire_incident.alarm_id). 알림 목록 · 대응 창 · SSE 의 fireId (docs/api/frontend-api-audit-1120.md 2장).
 * 화재가 아닌 알림은 없음(null).
 */
@Component
public class FireIds {

    private final NamedParameterJdbcTemplate jdbc;

    public FireIds(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Long, Long> byAlarm(Collection<Long> alarmIds) {
        Map<Long, Long> out = new HashMap<>();
        if (alarmIds == null || alarmIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT alarm_id, fire_id FROM fire_incident WHERE alarm_id IN (:ids)",
                new MapSqlParameterSource("ids", alarmIds), rs -> {
                    out.put(rs.getLong("alarm_id"), rs.getLong("fire_id"));
                });
        return out;
    }

    public Long byAlarm(long alarmId) {
        return byAlarm(List.of(alarmId)).get(alarmId);
    }
}
