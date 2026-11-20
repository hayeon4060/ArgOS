package com.gte.argos.gateway.log;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 이용 로그 (명세서 G2). activity_log 와 로그인 성공 기록(login_history)을 합쳐 보여준다.
 * 로그인 줄의 id 는 음수(-login_id_seq)라 작업 로그 id 와 겹치지 않는다.
 */
@Service
public class LogService {

    static final int MAX_ROWS = 50_000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final MasterCache masterCache;
    private final ActivityLogger activity;
    private final ObjectMapper mapper;

    public LogService(NamedParameterJdbcTemplate jdbc, CurrentUser currentUser, MasterCache masterCache,
                      ActivityLogger activity, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.masterCache = masterCache;
        this.activity = activity;
        this.mapper = mapper;
    }

    public record Criteria(LocalDate from, LocalDate to, Long factoryId, Long processId, Long userId, String type,
                           String q) {
    }

    public record Row(long id, OffsetDateTime at, Long userId, String loginId, String name, String role,
                      Long plantId, Long processId, String type, String message, String before, String after) {
    }

    /**
     * GET /logs. 유형별 건수는 유형 조건을 빼고 센다 (다른 유형 칸을 눌러 바꿀 수 있게, 화면과 같은 가정).
     * type 은 'LOGIN,LOGOUT' 처럼 쉼표로 여러 개를 받는다.
     */
    public Map<String, Object> list(Criteria c, int page, int size) {
        UserScope scope = scope(c);
        List<Row> scoped = rows(c, scope);
        Map<String, Integer> counts = new TreeMap<>();
        for (Row r : scoped) {
            counts.merge(r.type(), 1, Integer::sum);
        }
        List<Row> rows = byType(scoped, c.type());
        int[] byHour = new int[24];
        Map<String, Map<String, Object>> users = new LinkedHashMap<>();
        for (Row r : rows) {
            byHour[r.at().atZoneSameInstant(SimTime.ZONE).getHour()]++;
            if (r.loginId() != null) {
                Map<String, Object> u = users.computeIfAbsent(r.loginId(), k -> {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("loginId", r.loginId());
                    x.put("name", r.name());
                    x.put("count", 0);
                    return x;
                });
                u.put("count", (Integer) u.get("count") + 1);
            }
        }
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(Map.of("hour", h, "count", byHour[h]));
        }
        List<Map<String, Object>> top = new ArrayList<>(users.values());
        top.sort((a, b) -> (Integer) b.get("count") - (Integer) a.get("count"));
        int from = Math.min(rows.size(), page * size);
        List<Map<String, Object>> items = new ArrayList<>();
        MasterCache.Snapshot m = masterCache.get();
        for (Row r : rows.subList(from, Math.min(rows.size(), from + size))) {
            items.add(view(r, m));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items);
        out.put("countsByType", counts);
        out.put("byHour", hours);
        out.put("topUsers", top.subList(0, Math.min(4, top.size())));
        out.put("page", page);
        out.put("size", size);
        out.put("total", rows.size());
        return out;
    }

    static List<Row> byType(List<Row> rows, String type) {
        if (type == null || type.isBlank()) {
            return rows;
        }
        java.util.Set<String> types = new java.util.HashSet<>(List.of(type.split(",")));
        List<Row> out = new ArrayList<>();
        for (Row r : rows) {
            if (types.contains(r.type())) {
                out.add(r);
            }
        }
        return out;
    }

    /** GET /logs/export: 조건 그대로 CSV. 내려받은 것도 로그에 남는다 */
    public Export export(Criteria c) {
        UserScope scope = scope(c);
        List<Row> rows = byType(rows(c, scope), c.type());
        MasterCache.Snapshot m = masterCache.get();
        StringBuilder sb = new StringBuilder("﻿시각,아이디,이름,권한,범위,유형,내용\n");
        for (Row r : rows) {
            sb.append(csv(CSV_TIME.format(r.at().atZoneSameInstant(SimTime.ZONE)))).append(',')
                    .append(csv(r.loginId())).append(',').append(csv(r.name())).append(',')
                    .append(csv(r.role())).append(',').append(csv(scopeText(r, m))).append(',')
                    .append(csv(r.type())).append(',').append(csv(r.message())).append('\n');
        }
        activity.log(scope.userId(), "EXPORT", scope.siteId(), c.processId(), "activity_log", null,
                "이용 로그 내보내기 " + c.from() + " ~ " + c.to() + " (" + rows.size() + "건)");
        String name = "ArgOS_log_" + c.from().toString().replace("-", "") + "_"
                + c.to().toString().replace("-", "") + ".csv";
        return new Export(name, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    public record Export(String fileName, byte[] body) {
    }

    // ------------------------------------------------------------------

    private UserScope scope(Criteria c) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        if (c.from() == null || c.to() == null || c.to().isBefore(c.from())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "기간을 확인하세요");
        }
        if (c.processId() != null) {
            currentUser.checkProcess(scope, c.processId());
        } else if (c.factoryId() != null) {
            currentUser.checkFactory(scope, c.factoryId());
        }
        return scope;
    }

    List<Row> rows(Criteria c, UserScope scope) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", c.from().atStartOfDay(SimTime.ZONE).toOffsetDateTime())
                .addValue("to", c.to().plusDays(1).atStartOfDay(SimTime.ZONE).toOffsetDateTime())
                .addValue("limit", MAX_ROWS);
        StringBuilder where = new StringBuilder(" WHERE at >= :from AND at < :to");
        if (c.userId() != null) {
            where.append(" AND user_id = :userId");
            p.addValue("userId", c.userId());
        }
        if (c.q() != null && !c.q().isBlank()) {
            where.append(" AND (login_id ILIKE :q OR name ILIKE :q OR message ILIKE :q)");
            p.addValue("q", "%" + c.q().replace("%", "\\%").replace("_", "\\_") + "%");
        }
        String sql = """
                SELECT * FROM (
                  SELECT l.log_id AS id, l.created_at AS at, l.user_id, u.login_id, u.name, r.code AS role,
                         COALESCE(l.plant_id, u.plant_id) AS plant_id, l.process_id, l.type,
                         l.summary AS message, l.before::text AS before, l.after::text AS after
                  FROM activity_log l
                  LEFT JOIN app_user u ON u.user_id = l.user_id
                  LEFT JOIN role r ON r.role_id = u.role_id
                  UNION ALL
                  SELECT -h.login_id_seq, h.logged_at, h.user_id, u.login_id, u.name, r.code,
                         u.plant_id, NULL, 'LOGIN',
                         '로그인' || COALESCE(' (' || h.ip_addr || ')', ''), NULL, NULL
                  FROM login_history h
                  JOIN app_user u ON u.user_id = h.user_id
                  JOIN role r ON r.role_id = u.role_id
                  WHERE h.success
                ) t""" + where + " ORDER BY at DESC, id DESC LIMIT :limit";
        List<Row> all = jdbc.query(sql, p, (rs, i) -> new Row(rs.getLong("id"),
                rs.getObject("at", OffsetDateTime.class), (Long) rs.getObject("user_id"),
                rs.getString("login_id"), rs.getString("name"), rs.getString("role"),
                (Long) rs.getObject("plant_id"), (Long) rs.getObject("process_id"), rs.getString("type"),
                rs.getString("message"), rs.getString("before"), rs.getString("after")));
        MasterCache.Snapshot m = masterCache.get();
        List<Row> out = new ArrayList<>();
        for (Row r : all) {
            if (visible(r, scope, m) && matches(r, c, m)) {
                out.add(r);
            }
        }
        return out;
    }

    /** 공장 담당자는 자기 사업장 · 담당 공장 범위의 로그만 */
    static boolean visible(Row r, UserScope scope, MasterCache.Snapshot m) {
        if (scope.isHq()) {
            return true;
        }
        if (r.processId() != null) {
            return scope.canProcess(r.processId(), m);
        }
        return r.plantId() != null && scope.canSite(r.plantId(), m);
    }

    static boolean matches(Row r, Criteria c, MasterCache.Snapshot m) {
        if (c.processId() != null) {
            return r.processId() != null && r.processId().equals(c.processId());
        }
        if (c.factoryId() != null) {
            if (r.processId() == null) {
                return false;
            }
            CentralDto.Process p = m.process(r.processId());
            return p != null && p.factoryId() == c.factoryId();
        }
        return true;
    }

    private Map<String, Object> view(Row r, MasterCache.Snapshot m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", r.id());
        v.put("at", r.at().atZoneSameInstant(SimTime.ZONE).toOffsetDateTime());
        v.put("loginId", r.loginId());
        v.put("name", r.name());
        v.put("role", r.role());
        v.put("scopeText", scopeText(r, m));
        v.put("type", r.type());
        v.put("message", r.message());
        v.put("before", json(r.before()));
        v.put("after", json(r.after()));
        return v;
    }

    static String scopeText(Row r, MasterCache.Snapshot m) {
        if (r.processId() != null) {
            CentralDto.Process p = m.process(r.processId());
            if (p != null) {
                CentralDto.Factory f = m.factory(p.factoryId());
                return (f == null ? "" : f.name() + " ") + p.name();
            }
        }
        if (r.plantId() != null) {
            CentralDto.Plant pl = m.plant(r.plantId());
            if (pl != null) {
                return pl.name();
            }
        }
        return "전체";
    }

    private Object json(String s) {
        if (s == null) {
            return null;
        }
        try {
            return mapper.readValue(s, Object.class);
        } catch (Exception e) {
            return s;
        }
    }

    private static String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
