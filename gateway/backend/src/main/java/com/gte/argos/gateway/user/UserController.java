package com.gte.argos.gateway.user;

import com.gte.argos.gateway.common.api.ApiResponse;
import com.gte.argos.gateway.user.UserService.ProfileBody;
import com.gte.argos.gateway.user.UserService.UserBody;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 관리 (API 명세서 G1) · 내 정보 수정 (A1 PUT /me).
 * GET /me · PUT /me/password 는 로그인 쪽(AuthController)에 있다.
 */
@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String role,
                                                 @RequestParam(required = false) Long factoryId,
                                                 @RequestParam(required = false) Boolean active,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "15") int size) {
        return ApiResponse.ok(service.list(role, factoryId, active, q, page, size));
    }

    @GetMapping("/users/check-login-id")
    public ApiResponse<Map<String, Object>> checkLoginId(@RequestParam String loginId) {
        return ApiResponse.ok(service.checkLoginId(loginId));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping("/users")
    public ApiResponse<Map<String, Object>> create(@RequestBody UserBody body) {
        return ApiResponse.ok(service.create(body));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody UserBody body) {
        return ApiResponse.ok(service.update(id, body));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable long id, @RequestBody StatusBody body) {
        return ApiResponse.ok(service.setActive(id, body.active()));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Map<String, Object>> resetPassword(@PathVariable long id) {
        return ApiResponse.ok(service.resetPassword(id));
    }

    @PutMapping("/me")
    public ApiResponse<Map<String, Object>> updateMe(@RequestBody ProfileBody body) {
        return ApiResponse.ok(service.updateMe(body));
    }

    public record StatusBody(Boolean active) {
    }
}
