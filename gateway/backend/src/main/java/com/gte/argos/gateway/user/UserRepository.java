package com.gte.argos.gateway.user;

import com.gte.argos.gateway.central.SimTime;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 사용자 관리용 app_user · user_assignment 읽기·쓰기 (로그인 쪽 AuthRepository 는 건드리지 않는다) */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 배정 한 줄 (공장 담당자: factory 만, 공정 담당자: process 까지) */
    public record Assignment(Long plantId, Long factoryId, Long processId) {
    }

    public record UserRow(long id, Long plantId, String loginId, String name, String phone, String role,
                          boolean active, OffsetDateTime createdAt, OffsetDateTime lastLoginAt, boolean pushReady,
                          List<Assignment> assignments) {
    }

    public record HistoryRow(OffsetDateTime at, String by, String summary) {
    }

    private static final String SELECT = """
            SELECT u.user_id, u.plant_id, u.login_id, u.name, u.phone, r.code AS role, u.is_active, u.created_at,
                   u.fcm_token IS NOT NULL AS push_ready,
                   (SELECT MAX(h.logged_at) FROM login_history h WHERE h.user_id = u.user_id AND h.success) AS last_login
              FROM app_user u JOIN role r ON r.role_id = u.role_id
             WHERE u.deleted_at IS NULL
            """;

    /** 삭제 안 된 모든 사용자 (사용자 수가 많지 않아 화면 조건·권한 범위는 서비스에서 거른다) */
    public List<UserRow> findAll() {
        Map<Long, List<Assignment>> assignments = new HashMap<>();
        jdbc.query("SELECT user_id, plant_id, factory_id, process_id FROM user_assignment ORDER BY assignment_id", rs -> {
            assignments.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                    .add(new Assignment((Long) rs.getObject(2), (Long) rs.getObject(3), (Long) rs.getObject(4)));
        });
        return jdbc.query(SELECT + " ORDER BY u.user_id",
                (rs, n) -> row(rs, assignments.getOrDefault(rs.getLong("user_id"), List.of())));
    }

    public UserRow find(long id) {
        List<Assignment> assignments = jdbc.query(
                "SELECT plant_id, factory_id, process_id FROM user_assignment WHERE user_id = ? ORDER BY assignment_id",
                (rs, n) -> new Assignment((Long) rs.getObject(1), (Long) rs.getObject(2), (Long) rs.getObject(3)), id);
        return jdbc.query(SELECT + " AND u.user_id = ?", (rs, n) -> row(rs, assignments), id)
                .stream().findFirst().orElse(null);
    }

    private static UserRow row(ResultSet rs, List<Assignment> assignments) throws SQLException {
        return new UserRow(rs.getLong("user_id"), (Long) rs.getObject("plant_id"), rs.getString("login_id"),
                rs.getString("name"), rs.getString("phone"), rs.getString("role"), rs.getBoolean("is_active"),
                seoul(rs, "created_at"), seoul(rs, "last_login"),
                rs.getBoolean("push_ready"), List.copyOf(assignments));
    }

    /** DB 시각을 서울 시간(+09:00)으로 */
    private static OffsetDateTime seoul(ResultSet rs, String col) throws SQLException {
        OffsetDateTime v = rs.getObject(col, OffsetDateTime.class);
        return v == null ? null : v.atZoneSameInstant(SimTime.ZONE).toOffsetDateTime();
    }

    /** 삭제한 사용자 아이디도 다시 쓸 수 없다 (login_id UNIQUE) */
    public boolean loginIdTaken(String loginId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM app_user WHERE lower(login_id) = lower(?))", Boolean.class, loginId));
    }

    public long insert(Long plantId, String role, String loginId, String passwordHash, String name, String phone) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (plant_id, role_id, login_id, password_hash, name, phone,
                                      is_active, pw_reset_required, created_at, updated_at)
                VALUES (?, (SELECT role_id FROM role WHERE code = ?), ?, ?, ?, ?, true, true, now(), now())
                RETURNING user_id
                """, Long.class, plantId, role, loginId, passwordHash, name, phone);
    }

    public void update(long id, Long plantId, String role, String name, String phone) {
        jdbc.update("""
                UPDATE app_user SET plant_id = ?, role_id = (SELECT role_id FROM role WHERE code = ?),
                       name = ?, phone = ?, updated_at = now()
                 WHERE user_id = ?
                """, plantId, role, name, phone, id);
    }

    public void updateProfile(long id, String name, String phone) {
        jdbc.update("UPDATE app_user SET name = ?, phone = ?, updated_at = now() WHERE user_id = ?", name, phone, id);
    }

    public void replaceAssignments(long userId, List<Assignment> assignments) {
        jdbc.update("DELETE FROM user_assignment WHERE user_id = ?", userId);
        for (Assignment a : assignments) {
            jdbc.update("INSERT INTO user_assignment (user_id, plant_id, factory_id, process_id) VALUES (?, ?, ?, ?)",
                    userId, a.plantId(), a.factoryId(), a.processId());
        }
    }

    public void setActive(long id, boolean active) {
        jdbc.update("UPDATE app_user SET is_active = ?, updated_at = now() WHERE user_id = ?", active, id);
    }

    public void markDeleted(long id) {
        jdbc.update("UPDATE app_user SET is_active = false, deleted_at = now(), updated_at = now() WHERE user_id = ?", id);
    }

    public void resetPassword(long id, String passwordHash) {
        jdbc.update("""
                UPDATE app_user SET password_hash = ?, pw_reset_required = true, updated_at = now()
                 WHERE user_id = ?
                """, passwordHash, id);
    }

    /** 끝나지 않은 체크리스트 수 (보냄 · 진행 중 · 보고됨-확인 대기) */
    public int activeChecklists(long userId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM checklist
                 WHERE assignee_id = ? AND status IN ('SENT', 'IN_PROGRESS', 'REPORTED')
                """, Integer.class, userId);
        return n == null ? 0 : n;
    }

    /** 이 사용자를 대상으로 한 변경 기록 + 본인이 바꾼 기록 (본인 비밀번호 변경 등), 최근 순 */
    public List<HistoryRow> history(long userId, int limit) {
        return jdbc.query("""
                SELECT a.created_at, COALESCE(u.name, '시스템') AS by_name, a.summary
                  FROM activity_log a LEFT JOIN app_user u ON u.user_id = a.user_id
                 WHERE (a.target_table = 'app_user' AND a.target_id = ?)
                    OR (a.user_id = ? AND a.type = 'USER' AND a.target_table IS NULL)
                 ORDER BY a.created_at DESC, a.log_id DESC
                 LIMIT ?
                """, (rs, n) -> new HistoryRow(seoul(rs, "created_at"), rs.getString(2), rs.getString(3)),
                userId, userId, limit);
    }
}
