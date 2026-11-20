package com.gte.argos.gateway.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 · 로그아웃 · 로그인 연장 · 내 정보.
 * 웹은 JWT(Bearer) 하나로 인증한다 (refresh 쿠키 argos_rt 없음, 2026-11-18 프론트 요청).
 * 앱(APP)만 본문 refreshToken 으로 /auth/refresh 를 쓸 수 있다.
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService service;
    private final com.gte.argos.gateway.user.UserService users;

    public AuthController(AuthService service, com.gte.argos.gateway.user.UserService users) {
        this.service = service;
        this.users = users;
    }

    public record LoginRequest(@NotBlank String loginId, @NotBlank String password,
            @NotBlank String client) {}
    public record RefreshRequest(String refreshToken) {}
    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {
        AuthService.LoginResult result = service.login(request.loginId(), request.password(),
                request.client(), http.getRemoteAddr());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", result.accessToken());
        data.put("expiresIn", result.expiresIn());
        data.put("sessionExpiresAt", result.sessionExpiry());
        data.put("pwResetRequired", result.pwResetRequired());
        data.put("firstPage", result.firstPage());
        data.put("user", result.user());
        if ("APP".equals(request.client())) {
            data.put("refreshToken", result.refreshToken());
        }
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    /** 앱 전용. 본문 { refreshToken } */
    @PostMapping("/auth/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody(required = false) RefreshRequest request) {
        String refreshToken = request == null ? null : request.refreshToken();
        AuthService.RefreshResult result = service.refresh(refreshToken, "APP");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", result.accessToken());
        data.put("expiresIn", result.expiresIn());
        data.put("sessionExpiresAt", result.sessionExpiry());
        data.put("refreshToken", result.refreshToken());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        service.logout(Long.parseLong(jwt.getSubject()),
                ((Number) jwt.getClaim("sessionId")).longValue(), request.getRemoteAddr());
        return ResponseEntity.ok(success(null));
    }

    /** 로그인 1시간 연장. 늘어난 만료 시각까지 쓸 수 있는 새 accessToken 을 준다 (이전 토큰도 만료 전까지는 유효) */
    @PostMapping("/auth/extend")
    public ResponseEntity<Map<String, Object>> extend(@AuthenticationPrincipal Jwt jwt) {
        AuthService.ExtendResult result = service.extend(Long.parseLong(jwt.getSubject()),
                ((Number) jwt.getClaim("sessionId")).longValue());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accessToken", result.accessToken());
        data.put("expiresIn", result.expiresIn());
        data.put("sessionExpiresAt", result.sessionExpiry());
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        AuthService.MeView me = service.me(userId);
        // MeView 그대로 + 담당 범위 문구 (backend-notes 14장, 2026-11-19)
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", me.id());
        data.put("loginId", me.loginId());
        data.put("name", me.name());
        data.put("phone", me.phone());
        data.put("role", me.role());
        data.put("scope", me.scope());
        data.put("scopeText", users.scopeTextOf(userId));
        data.put("pwResetRequired", me.pwResetRequired());
        data.put("lastLoginAt", me.lastLoginAt());
        return success(data);
    }

    @PutMapping("/me/password")
    public Map<String, Object> password(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordRequest body, HttpServletRequest request) {
        service.changePassword(Long.parseLong(jwt.getSubject()),
                body.currentPassword(), body.newPassword(), request.getRemoteAddr());
        return success(null);
    }

    @ExceptionHandler(AuthFailure.class)
    public ResponseEntity<Map<String, Object>> authFailure(AuthFailure failure) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", failure.code());
        error.put("message", failure.getMessage());
        error.putAll(failure.details());
        return ResponseEntity.status(failure.status())
                .body(Map.of("success", false, "error", error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("success", false, "error",
                        Map.of("code", "VALIDATION_FAILED", "message", "입력값을 확인해 주세요.")));
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", data);
        return body;
    }
}
