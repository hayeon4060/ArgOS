package com.gte.argos.gateway.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record User(long id, Long siteId, String loginId, String passwordHash,
            String name, String phone, String role, boolean active, boolean pwResetRequired) {}
    public record Assignment(Long siteId, Long factoryId, Long processId) {}
    public record RefreshSession(long id, long userId, String client, Instant expiresAt,
            Instant revokedAt) {}

    private static final String USER_SELECT = """
            SELECT u.user_id, u.plant_id, u.login_id, u.password_hash, u.name,
                   u.phone, r.code AS role, u.is_active, u.pw_reset_required
              FROM app_user u JOIN role r ON r.role_id = u.role_id
            """;

    public User findUser(String loginId) {
        return jdbc.query(USER_SELECT + " WHERE u.login_id = ?", (rs, row) ->
                new User(rs.getLong("user_id"), (Long) rs.getObject("plant_id"),
                        rs.getString("login_id"), rs.getString("password_hash"),
                        rs.getString("name"), rs.getString("phone"), rs.getString("role"),
                        rs.getBoolean("is_active"), rs.getBoolean("pw_reset_required")),
                loginId).stream().findFirst().orElse(null);
    }

    public User findActiveUser(long id) {
        return jdbc.query(USER_SELECT + " WHERE u.user_id = ? AND u.is_active = true", (rs, row) ->
                new User(rs.getLong("user_id"), (Long) rs.getObject("plant_id"),
                        rs.getString("login_id"), rs.getString("password_hash"),
                        rs.getString("name"), rs.getString("phone"), rs.getString("role"),
                        true, rs.getBoolean("pw_reset_required")),
                id).stream().findFirst().orElse(null);
    }

    public List<Assignment> assignments(long userId) {
        return jdbc.query("SELECT plant_id, factory_id, process_id FROM user_assignment WHERE user_id = ?",
                (rs, row) -> new Assignment((Long) rs.getObject(1),
                        (Long) rs.getObject(2), (Long) rs.getObject(3)), userId);
    }

    public List<Instant> recentFailures(String loginId, Instant after) {
        return jdbc.query("""
                SELECT logged_at FROM login_history
                 WHERE attempted_login_id = ? AND failure_code = 'AUTH_INVALID'
                   AND logged_at > ?
                   AND logged_at > COALESCE(
                     (SELECT MAX(logged_at) FROM login_history
                       WHERE attempted_login_id = ? AND success = true),
                     TIMESTAMPTZ '1970-01-01 00:00:00+00')
                   AND logged_at > COALESCE(
                     (SELECT locked_until FROM auth_login_lock WHERE login_id = ?),
                     TIMESTAMPTZ '1970-01-01 00:00:00+00')
                 ORDER BY logged_at
                """, (rs, row) -> rs.getTimestamp(1).toInstant(),
                loginId, Timestamp.from(after), loginId, loginId);
    }

    public Instant lockedUntil(String loginId) {
        return jdbc.query("SELECT locked_until FROM auth_login_lock WHERE login_id = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), loginId)
                .stream().findFirst().orElse(null);
    }

    public void lockLogin(String loginId, Instant until) {
        jdbc.update("""
                INSERT INTO auth_login_lock (login_id, locked_until) VALUES (?, ?)
                ON CONFLICT (login_id) DO UPDATE SET locked_until = EXCLUDED.locked_until
                """, loginId, Timestamp.from(until));
    }

    public void recordLogin(Long userId, String attemptedLoginId, String ip,
            boolean success, String failureCode) {
        jdbc.update("""
                INSERT INTO login_history
                    (user_id, attempted_login_id, logged_at, ip_addr, success, failure_code)
                VALUES (?, ?, now(), ?, ?, ?)
                """, userId, attemptedLoginId, ip, success, failureCode);
    }

    public long createSession(long userId, String client, String tokenHash, Instant expiry) {
        return jdbc.queryForObject("""
                INSERT INTO auth_refresh_session (user_id, client, token_hash, expires_at)
                VALUES (?, ?, ?, ?) RETURNING session_id
                """, Long.class, userId, client, tokenHash, Timestamp.from(expiry));
    }

    public RefreshSession findRefreshSession(String tokenHash) {
        return jdbc.query("""
                SELECT session_id, user_id, client, expires_at, revoked_at
                  FROM auth_refresh_session WHERE token_hash = ? FOR UPDATE
                """, (rs, row) -> new RefreshSession(rs.getLong(1), rs.getLong(2),
                        rs.getString(3), rs.getTimestamp(4).toInstant(),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()),
                tokenHash).stream().findFirst().orElse(null);
    }

    public boolean isSessionActive(long sessionId, long userId) {
        Boolean active = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM auth_refresh_session
                 WHERE session_id = ? AND user_id = ? AND revoked_at IS NULL
                   AND expires_at > now())
                """, Boolean.class, sessionId, userId);
        return Boolean.TRUE.equals(active);
    }

    public void rotateRefresh(long sessionId, String oldHash, String newHash) {
        int changed = jdbc.update("""
                UPDATE auth_refresh_session SET token_hash = ?
                 WHERE session_id = ? AND token_hash = ? AND revoked_at IS NULL
                   AND expires_at > now()
                """, newHash, sessionId, oldHash);
        if (changed != 1) throw new AuthFailure(401, "REFRESH_EXPIRED", "다시 로그인해 주세요.");
    }

    public void revokeSession(long sessionId, long userId) {
        jdbc.update("""
                UPDATE auth_refresh_session SET revoked_at = now()
                 WHERE session_id = ? AND user_id = ? AND revoked_at IS NULL
                """, sessionId, userId);
    }

    public RefreshSession findSession(long sessionId, long userId) {
        return jdbc.query("""
                SELECT session_id, user_id, client, expires_at, revoked_at
                  FROM auth_refresh_session
                 WHERE session_id = ? AND user_id = ? FOR UPDATE
                """, (rs, row) -> new RefreshSession(rs.getLong(1), rs.getLong(2),
                        rs.getString(3), rs.getTimestamp(4).toInstant(),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()),
                sessionId, userId).stream().findFirst().orElse(null);
    }

    public Instant extendSession(long sessionId) {
        Timestamp expiry = jdbc.queryForObject("""
                UPDATE auth_refresh_session
                   SET expires_at = expires_at + INTERVAL '1 hour'
                 WHERE session_id = ? AND revoked_at IS NULL AND expires_at > now()
                 RETURNING expires_at
                """, Timestamp.class, sessionId);
        return expiry.toInstant();
    }

    public Instant lastLogin(long userId) {
        Timestamp ts = jdbc.queryForObject("""
                SELECT MAX(logged_at) FROM login_history WHERE user_id = ? AND success = true
                """, Timestamp.class, userId);
        return ts == null ? null : ts.toInstant();
    }

    public void changePassword(long userId, String hash) {
        jdbc.update("""
                UPDATE app_user SET password_hash = ?, pw_reset_required = false,
                       updated_at = now() WHERE user_id = ?
                """, hash, userId);
    }

    public void recordAction(long userId, String type, String summary, String ip) {
        jdbc.update("""
                INSERT INTO activity_log (user_id, type, summary, ip_addr, created_at)
                VALUES (?, ?, ?, ?, now())
                """, userId, type, summary, ip);
    }
}
