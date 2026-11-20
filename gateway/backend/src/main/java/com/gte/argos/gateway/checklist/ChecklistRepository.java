package com.gte.argos.gateway.checklist;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
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

/**
 * 연계 DB 의 경보 대응 테이블: checklist, checklist_item, checklist_template, alarm_action.
 * 시각은 모두 시연 시계 값을 받아 넣는다 (DB now() 를 쓰지 않음).
 */
@Repository
public class ChecklistRepository {

    public static final List<String> ACTIVE = List.of("SENT", "IN_PROGRESS", "REPORTED");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ChecklistRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ---------------------------------------------------------------
    // 체크리스트
    // ---------------------------------------------------------------

    public long create(long alarmId, Long templateId, long assigneeId, long sentBy, OffsetDateTime sentAt,
                       List<String> items) {
        Long id = jdbc.queryForObject("""
                INSERT INTO checklist (alarm_id, template_id, assignee_id, status, sent_by, sent_at)
                VALUES (:alarmId, :templateId, :assigneeId, 'SENT', :sentBy, :sentAt) RETURNING checklist_id
                """, new MapSqlParameterSource().addValue("alarmId", alarmId).addValue("templateId", templateId)
                .addValue("assigneeId", assigneeId).addValue("sentBy", sentBy).addValue("sentAt", sentAt), Long.class);
        int seq = 1;
        for (String content : items) {
            jdbc.update("INSERT INTO checklist_item (checklist_id, seq, content) VALUES (:id, :seq, :content)",
                    new MapSqlParameterSource().addValue("id", id).addValue("seq", seq++).addValue("content", content));
        }
        return id;
    }

    public Checklist find(long id) {
        List<Checklist> rows = jdbc.query("""
                SELECT c.*, a.name AS assignee_name, s.name AS sent_by_name, cf.name AS confirmed_by_name
                FROM checklist c
                JOIN app_user a ON a.user_id = c.assignee_id
                LEFT JOIN app_user s ON s.user_id = c.sent_by
                LEFT JOIN app_user cf ON cf.user_id = c.confirmed_by
                WHERE c.checklist_id = :id
                """, new MapSqlParameterSource("id", id), (rs, i) -> checklist(rs));
        if (rows.isEmpty()) {
            return null;
        }
        Checklist c = rows.get(0);
        c.items().addAll(items(id));
        return c;
    }

    public List<Item> items(long checklistId) {
        return jdbc.query("SELECT * FROM checklist_item WHERE checklist_id = :id ORDER BY seq",
                new MapSqlParameterSource("id", checklistId), (rs, i) -> new Item(rs.getLong("item_id"),
                        rs.getInt("seq"), rs.getString("content"), odt(rs, "done_at"), rs.getString("result"),
                        rs.getString("memo")));
    }

    /** 진행 중(SENT · IN_PROGRESS · REPORTED) 체크리스트 */
    public List<Checklist> active(Long assigneeId) {
        return list(ACTIVE, assigneeId, null);
    }

    /** 상태·담당자·경보로 찾기 (null 은 조건 없음) */
    public List<Checklist> list(Collection<String> statuses, Long assigneeId, Collection<Long> alarmIds) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.*, a.name AS assignee_name, s.name AS sent_by_name, cf.name AS confirmed_by_name
                FROM checklist c
                JOIN app_user a ON a.user_id = c.assignee_id
                LEFT JOIN app_user s ON s.user_id = c.sent_by
                LEFT JOIN app_user cf ON cf.user_id = c.confirmed_by
                WHERE 1 = 1
                """);
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (statuses != null && !statuses.isEmpty()) {
            sql.append(" AND c.status IN (:statuses)");
            p.addValue("statuses", statuses);
        }
        if (assigneeId != null) {
            sql.append(" AND c.assignee_id = :assigneeId");
            p.addValue("assigneeId", assigneeId);
        }
        if (alarmIds != null) {
            if (alarmIds.isEmpty()) {
                return new ArrayList<>();
            }
            sql.append(" AND c.alarm_id IN (:alarmIds)");
            p.addValue("alarmIds", alarmIds);
        }
        sql.append(" ORDER BY c.sent_at DESC");
        List<Checklist> rows = jdbc.query(sql.toString(), p, (rs, i) -> checklist(rs));
        if (!rows.isEmpty()) {
            Map<Long, Checklist> byId = new HashMap<>();
            rows.forEach(c -> byId.put(c.id(), c));
            jdbc.query("SELECT * FROM checklist_item WHERE checklist_id IN (:ids) ORDER BY checklist_id, seq",
                    new MapSqlParameterSource("ids", byId.keySet()), rs -> {
                        byId.get(rs.getLong("checklist_id")).items().add(new Item(rs.getLong("item_id"),
                                rs.getInt("seq"), rs.getString("content"), odt(rs, "done_at"),
                                rs.getString("result"), rs.getString("memo")));
                    });
        }
        return rows;
    }

    /** 이 경보에 진행 중인 체크리스트 (없으면 null) */
    public Checklist activeForAlarm(long alarmId) {
        List<Checklist> rows = list(ACTIVE, null, List.of(alarmId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 이 경보의 마지막 체크리스트 (상태 무관, 없으면 null) */
    public Checklist latestForAlarm(long alarmId) {
        List<Checklist> rows = list(null, null, List.of(alarmId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int updateItem(long checklistId, long itemId, boolean done, String result, String memo, OffsetDateTime at) {
        return jdbc.update("""
                UPDATE checklist_item SET done_at = :doneAt, result = :result, memo = :memo
                WHERE checklist_id = :cid AND item_id = :iid
                """, new MapSqlParameterSource().addValue("doneAt", done ? at : null)
                .addValue("result", done ? result : null).addValue("memo", memo)
                .addValue("cid", checklistId).addValue("iid", itemId));
    }

    public void setStatus(long checklistId, String status) {
        jdbc.update("UPDATE checklist SET status = :s WHERE checklist_id = :id",
                new MapSqlParameterSource().addValue("s", status).addValue("id", checklistId));
    }

    public void report(long checklistId, OffsetDateTime at) {
        jdbc.update("UPDATE checklist SET status = 'REPORTED', reported_at = :at WHERE checklist_id = :id",
                new MapSqlParameterSource().addValue("at", at).addValue("id", checklistId));
    }

    public void confirm(long checklistId, long userId, OffsetDateTime at) {
        jdbc.update("""
                UPDATE checklist SET status = 'CONFIRMED', confirmed_by = :u, confirmed_at = :at WHERE checklist_id = :id
                """, new MapSqlParameterSource().addValue("u", userId).addValue("at", at).addValue("id", checklistId));
    }

    public void remind(long checklistId, OffsetDateTime at) {
        jdbc.update("""
                UPDATE checklist SET remind_count = remind_count + 1, last_remind_at = :at WHERE checklist_id = :id
                """, new MapSqlParameterSource().addValue("at", at).addValue("id", checklistId));
    }

    public void reassign(long checklistId, long assigneeId) {
        jdbc.update("UPDATE checklist SET assignee_id = :a WHERE checklist_id = :id",
                new MapSqlParameterSource().addValue("a", assigneeId).addValue("id", checklistId));
    }

    /** 대시보드: 열린 경보(alarmId → 설비) 중 진행 중 체크리스트가 있는 설비 → checklistId */
    public Map<Long, Long> activeChecklistByEquipment(Map<Long, Long> alarmToEquipment) {
        Map<Long, Long> out = new HashMap<>();
        if (alarmToEquipment.isEmpty()) {
            return out;
        }
        jdbc.query("""
                SELECT checklist_id, alarm_id FROM checklist
                WHERE alarm_id IN (:ids) AND status IN (:active) ORDER BY sent_at
                """, new MapSqlParameterSource().addValue("ids", alarmToEquipment.keySet()).addValue("active", ACTIVE),
                rs -> {
                    Long eq = alarmToEquipment.get(rs.getLong("alarm_id"));
                    if (eq != null) {
                        out.put(eq, rs.getLong("checklist_id"));
                    }
                });
        return out;
    }

    /** 대시보드·상세에 붙이는 요약 */
    public Map<String, Object> summary(long checklistId) {
        Checklist c = find(checklistId);
        if (c == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("status", c.status());
        m.put("doneCount", c.doneCount());
        m.put("totalCount", c.items().size());
        m.put("assignee", Map.of("id", c.assigneeId(), "name", c.assigneeName()));
        return m;
    }

    // ---------------------------------------------------------------
    // 체크리스트 양식
    // ---------------------------------------------------------------

    /** 설비 종류·센서 종류에 맞는 양식. 정확히 없으면 센서 종류만 맞는 것 */
    public Template template(String equipmentType, String sensorType, int stage) {
        List<Template> rows = jdbc.query("""
                SELECT * FROM checklist_template
                WHERE is_active AND sensor_type = :st
                ORDER BY (equipment_type = :et) DESC, (stage = :stage) DESC, template_id
                LIMIT 1
                """, new MapSqlParameterSource().addValue("st", sensorType).addValue("et", equipmentType)
                .addValue("stage", stage), (rs, i) -> template(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Template template(long templateId) {
        List<Template> rows = jdbc.query("SELECT * FROM checklist_template WHERE template_id = :id",
                new MapSqlParameterSource("id", templateId), (rs, i) -> template(rs));
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---------------------------------------------------------------
    // 경보 조치 이력
    // ---------------------------------------------------------------

    public void addAction(long alarmId, String action, String target, String result, String detail, Long userId,
                          OffsetDateTime at) {
        jdbc.update("""
                INSERT INTO alarm_action (alarm_id, action, target, result, detail, user_id, done_at)
                VALUES (:alarmId, :action, :target, :result, :detail, :userId, :at)
                """, new MapSqlParameterSource().addValue("alarmId", alarmId).addValue("action", action)
                .addValue("target", target).addValue("result", result == null ? "OK" : result)
                .addValue("detail", detail).addValue("userId", userId).addValue("at", at));
    }

    public List<Action> actions(long alarmId) {
        return jdbc.query("""
                SELECT aa.*, u.name AS user_name FROM alarm_action aa LEFT JOIN app_user u ON u.user_id = aa.user_id
                WHERE aa.alarm_id = :id ORDER BY aa.done_at, aa.action_id
                """, new MapSqlParameterSource("id", alarmId), (rs, i) -> action(rs));
    }

    /** 설비 상세 이벤트 이력의 ACTION 줄 */
    public List<Map<String, Object>> actionsForEquipmentAlarms(List<Long> alarmIds, OffsetDateTime[] range) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (alarmIds.isEmpty()) {
            return out;
        }
        List<Action> rows = jdbc.query("""
                SELECT aa.*, u.name AS user_name FROM alarm_action aa LEFT JOIN app_user u ON u.user_id = aa.user_id
                WHERE aa.alarm_id IN (:ids) AND aa.done_at >= :from AND aa.done_at < :to
                ORDER BY aa.done_at DESC
                """, new MapSqlParameterSource().addValue("ids", alarmIds).addValue("from", range[0])
                .addValue("to", range[1]), (rs, i) -> action(rs));
        for (Action a : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", a.doneAt());
            m.put("type", "ACTION");
            m.put("text", actionText(a));
            m.put("by", a.userName());
            m.put("alarmId", a.alarmId());
            out.add(m);
        }
        return out;
    }

    public static String actionText(Action a) {
        return switch (a.action()) {
            case "ACK" -> "알림 확인";
            case "CHECKLIST" -> "체크리스트 전송" + (a.target() == null ? "" : " → " + a.target());
            case "PUSH" -> "앱 알림 전송" + (a.target() == null ? "" : " → " + a.target());
            case "REASSIGN" -> "담당자 변경" + (a.target() == null ? "" : " → " + a.target());
            case "REPORT" -> "체크리스트 완료 보고";
            case "CONFIRM" -> "조치 확인";
            case "CLOSE" -> "알림 종료";
            case "SHUTDOWN" -> "설비 셧다운" + (a.target() == null ? "" : " · " + a.target());
            case "FAN" -> "배기팬 조작" + (a.target() == null ? "" : " · " + a.target());
            case "CALL" -> "공정 담당자 호출";
            case "BROADCAST" -> "경고 방송";
            case "EVACUATED" -> "대피 확인";
            default -> a.detail() == null ? a.action() : a.detail();
        };
    }

    // ---------------------------------------------------------------
    // 사용자
    // ---------------------------------------------------------------

    public Map<Long, String> userNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        List<Long> clean = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (clean.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT user_id, name FROM app_user WHERE user_id IN (:ids)", new MapSqlParameterSource("ids", clean),
                rs -> {
                    out.put(rs.getLong("user_id"), rs.getString("name"));
                });
        return out;
    }

    /** 공정에 배정된 공정 담당자 */
    public List<Worker> workers(long processId) {
        return jdbc.query("""
                SELECT DISTINCT u.user_id, u.name, u.login_id, u.fcm_token IS NOT NULL AS push_ready
                FROM user_assignment ua
                JOIN app_user u ON u.user_id = ua.user_id
                JOIN role r ON r.role_id = u.role_id
                WHERE ua.process_id = :pid AND r.code = 'WORKER' AND u.is_active
                ORDER BY u.name
                """, new MapSqlParameterSource("pid", processId), (rs, i) -> new Worker(rs.getLong("user_id"),
                rs.getString("name"), rs.getString("login_id"), rs.getBoolean("push_ready")));
    }

    public String fcmToken(long userId) {
        List<String> rows = jdbc.query("SELECT fcm_token FROM app_user WHERE user_id = :id",
                new MapSqlParameterSource("id", userId), (rs, i) -> rs.getString(1));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void setFcmToken(long userId, String token) {
        jdbc.update("UPDATE app_user SET fcm_token = :t, updated_at = now() WHERE user_id = :id",
                new MapSqlParameterSource().addValue("t", token).addValue("id", userId));
    }

    // ---------------------------------------------------------------

    private Checklist checklist(ResultSet rs) throws SQLException {
        return new Checklist(rs.getLong("checklist_id"), rs.getLong("alarm_id"), rs.getObject("template_id", Long.class),
                rs.getLong("assignee_id"), rs.getString("assignee_name"), rs.getString("status"),
                rs.getObject("sent_by", Long.class), rs.getString("sent_by_name"), odt(rs, "sent_at"),
                rs.getInt("remind_count"), odt(rs, "last_remind_at"), odt(rs, "reported_at"),
                rs.getObject("confirmed_by", Long.class), rs.getString("confirmed_by_name"), odt(rs, "confirmed_at"),
                new ArrayList<>());
    }

    private Template template(ResultSet rs) throws SQLException {
        List<String> items;
        try {
            items = mapper.readValue(rs.getString("items"), new TypeReference<List<String>>() { });
        } catch (Exception e) {
            items = List.of();
        }
        return new Template(rs.getLong("template_id"), rs.getString("equipment_type"), rs.getString("sensor_type"),
                rs.getInt("stage"), rs.getString("cause"), items);
    }

    private Action action(ResultSet rs) throws SQLException {
        return new Action(rs.getLong("action_id"), rs.getLong("alarm_id"), rs.getString("action"),
                rs.getString("target"), rs.getString("result"), rs.getString("detail"),
                rs.getObject("user_id", Long.class), rs.getString("user_name"), odt(rs, "done_at"));
    }

    private static OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.atZoneSameInstant(com.gte.argos.gateway.central.SimTime.ZONE).toOffsetDateTime();
    }

    // ---------------------------------------------------------------

    public record Checklist(long id, long alarmId, Long templateId, long assigneeId, String assigneeName,
                            String status, Long sentBy, String sentByName, OffsetDateTime sentAt, int remindCount,
                            OffsetDateTime lastRemindAt, OffsetDateTime reportedAt, Long confirmedBy,
                            String confirmedByName, OffsetDateTime confirmedAt, List<Item> items) {

        public int doneCount() {
            int n = 0;
            for (Item i : items) {
                if (i.doneAt() != null) {
                    n++;
                }
            }
            return n;
        }

        public boolean isActive() {
            return ACTIVE.contains(status);
        }
    }

    public record Item(long id, int seq, String content, OffsetDateTime doneAt, String result, String memo) {
    }

    public record Template(long id, String equipmentType, String sensorType, int stage, String cause, List<String> items) {
    }

    public record Action(long id, long alarmId, String action, String target, String result, String detail,
                         Long userId, String userName, OffsetDateTime doneAt) {
    }

    public record Worker(long id, String name, String loginId, boolean pushReady) {
    }
}
