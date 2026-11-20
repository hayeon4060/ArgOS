package com.gte.argos.gateway.auth;

import com.gte.argos.gateway.auth.AuthRepository.Assignment;
import com.gte.argos.gateway.auth.AuthRepository.RefreshSession;
import com.gte.argos.gateway.auth.AuthRepository.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final AuthTokens tokens;
    private final PasswordEncoder passwords;

    public AuthService(AuthRepository repository, AuthTokens tokens, PasswordEncoder passwords) {
        this.repository = repository;
        this.tokens = tokens;
        this.passwords = passwords;
    }

    public record LoginResult(String accessToken, long expiresIn, boolean pwResetRequired,
            String firstPage, UserView user, String refreshToken, Instant sessionExpiry) {}
    public record RefreshResult(String accessToken, long expiresIn, String refreshToken,
            String client, Instant sessionExpiry) {}
    public record ExtendResult(String accessToken, long expiresIn, Instant sessionExpiry) {}
    public record Scope(Long siteId, List<Long> factoryIds, List<Long> processIds) {}
    public record UserView(long id, String loginId, String name, String role, Scope scope) {}
    public record MeView(long id, String loginId, String name, String phone,
            String role, Scope scope, boolean pwResetRequired, Instant lastLoginAt) {}

    @Transactional(noRollbackFor = AuthFailure.class)
    public LoginResult login(String loginId, String password, String client, String ip) {
        if (!"WEB".equals(client) && !"APP".equals(client)) {
            throw new AuthFailure(400, "VALIDATION_FAILED", "client는 WEB 또는 APP이어야 합니다.");
        }
        User user = repository.findUser(loginId);
        Instant lockedUntil = repository.lockedUntil(loginId);
        if (lockedUntil != null && lockedUntil.isAfter(Instant.now())) {
            throw new AuthFailure(423, "AUTH_LOCKED", "10분 뒤 다시 시도해 주세요.",
                    Map.of("unlockAt", lockedUntil.toString()));
        }
        List<Instant> failures = repository.recentFailures(loginId,
                Instant.now().minus(10, ChronoUnit.MINUTES));
        if (user == null || !passwords.matches(password, user.passwordHash())) {
            repository.recordLogin(user == null ? null : user.id(), loginId, ip,
                    false, "AUTH_INVALID");
            int remaining = 4 - failures.size();
            if (remaining == 0) {
                Instant nextUnlock = Instant.now().plus(10, ChronoUnit.MINUTES);
                repository.lockLogin(loginId, nextUnlock);
                throw new AuthFailure(423, "AUTH_LOCKED", "10분 뒤 다시 시도해 주세요.",
                        Map.of("unlockAt", nextUnlock.toString()));
            }
            throw new AuthFailure(401, "AUTH_INVALID", "아이디 또는 비밀번호가 틀렸습니다.",
                    Map.of("remaining", remaining));
        }
        if (!user.active()) {
            repository.recordLogin(user.id(), loginId, ip, false, "USER_INACTIVE");
            throw new AuthFailure(403, "USER_INACTIVE", "사용 정지된 계정입니다.");
        }
        boolean worker = "WORKER".equals(user.role());
        if ((worker && !"APP".equals(client)) || (!worker && !"WEB".equals(client))) {
            repository.recordLogin(user.id(), loginId, ip, false, "CLIENT_NOT_ALLOWED");
            throw new AuthFailure(403, "CLIENT_NOT_ALLOWED", "이 계정에서 사용할 수 없는 기기입니다.");
        }
        repository.recordLogin(user.id(), loginId, ip, true, null);
        Instant sessionExpiry = Instant.now().plusSeconds(tokens.sessionSeconds());
        String refresh = tokens.newRefreshToken();
        long sessionId = repository.createSession(user.id(), client, tokens.hash(refresh), sessionExpiry);
        String firstPage = switch (user.role()) {
            case "HQ_ADMIN" -> "SITE_SELECT";
            case "PLANT_ADMIN" -> "FACTORY_DASHBOARD";
            default -> "APP_HOME";
        };
        return new LoginResult(tokens.accessToken(user.id(), sessionId, sessionExpiry),
                tokens.accessSeconds(), user.pwResetRequired(), firstPage,
                view(user), refresh, sessionExpiry);
    }

    @Transactional
    public RefreshResult refresh(String refreshToken, String expectedClient) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthFailure(401, "REFRESH_EXPIRED", "다시 로그인해 주세요.");
        }
        String oldHash = tokens.hash(refreshToken);
        RefreshSession session = repository.findRefreshSession(oldHash);
        if (session == null || session.revokedAt() != null
                || !session.expiresAt().isAfter(Instant.now())
                || !expectedClient.equals(session.client())) {
            throw new AuthFailure(401, "REFRESH_EXPIRED", "다시 로그인해 주세요.");
        }
        User user = repository.findActiveUser(session.userId());
        if (user == null) throw new AuthFailure(401, "REFRESH_EXPIRED", "다시 로그인해 주세요.");
        String nextRefresh = tokens.newRefreshToken();
        repository.rotateRefresh(session.id(), oldHash, tokens.hash(nextRefresh));
        return new RefreshResult(tokens.accessToken(user.id(), session.id(), session.expiresAt()),
                tokens.accessSeconds(), nextRefresh, session.client(), session.expiresAt());
    }

    @Transactional
    public void logout(long userId, long sessionId, String ip) {
        repository.revokeSession(sessionId, userId);
        repository.recordAction(userId, "LOGOUT", "로그아웃", ip);
    }

    @Transactional
    public ExtendResult extend(long userId, long sessionId) {
        RefreshSession session = repository.findSession(sessionId, userId);
        if (session == null || session.revokedAt() != null
                || !session.expiresAt().isAfter(Instant.now())) {
            throw new AuthFailure(401, "AUTH_REQUIRED", "다시 로그인해 주세요.");
        }
        if (repository.findActiveUser(userId) == null) {
            throw new AuthFailure(401, "AUTH_REQUIRED", "다시 로그인해 주세요.");
        }
        Instant sessionExpiry = repository.extendSession(sessionId);
        return new ExtendResult(tokens.accessToken(userId, sessionId, sessionExpiry),
                expiresIn(Instant.now(), tokens.accessSeconds(), sessionExpiry), sessionExpiry);
    }

    /** 새 토큰의 남은 초 = min(토큰 유효 시간, 세션 만료까지) */
    static long expiresIn(Instant now, long accessSeconds, Instant sessionExpiry) {
        long untilSession = Math.max(0, sessionExpiry.getEpochSecond() - now.getEpochSecond());
        return Math.min(accessSeconds, untilSession);
    }

    @Transactional(readOnly = true)
    public MeView me(long userId) {
        User user = repository.findActiveUser(userId);
        if (user == null) throw new AuthFailure(401, "AUTH_REQUIRED", "다시 로그인해 주세요.");
        return new MeView(user.id(), user.loginId(), user.name(), user.phone(),
                user.role(), scope(user), user.pwResetRequired(), repository.lastLogin(userId));
    }

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword, String ip) {
        User user = repository.findActiveUser(userId);
        if (user == null) throw new AuthFailure(401, "AUTH_REQUIRED", "다시 로그인해 주세요.");
        if (!passwords.matches(currentPassword, user.passwordHash())) {
            throw new AuthFailure(401, "PASSWORD_MISMATCH", "현재 비밀번호가 틀렸습니다.");
        }
        if (newPassword == null || !newPassword.matches("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")) {
            throw new AuthFailure(400, "PASSWORD_RULE", "비밀번호는 8자 이상이며 영문·숫자·특수문자를 포함해야 합니다.");
        }
        repository.changePassword(userId, passwords.encode(newPassword));
        repository.recordAction(userId, "USER", "본인 비밀번호 변경", ip);
    }

    private UserView view(User user) {
        return new UserView(user.id(), user.loginId(), user.name(), user.role(), scope(user));
    }

    private Scope scope(User user) {
        List<Assignment> assignments = repository.assignments(user.id());
        Set<Long> factories = new LinkedHashSet<>();
        Set<Long> processes = new LinkedHashSet<>();
        Long siteId = user.siteId();
        for (Assignment assignment : assignments) {
            if (siteId == null) siteId = assignment.siteId();
            if (assignment.factoryId() != null) factories.add(assignment.factoryId());
            if (assignment.processId() != null) processes.add(assignment.processId());
        }
        return new Scope(siteId, List.copyOf(factories), List.copyOf(processes));
    }
}
