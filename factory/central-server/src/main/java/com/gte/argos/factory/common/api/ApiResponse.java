package com.gte.argos.factory.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 공통 응답 모양. 연계 서버·프론트와 같은 형식
 * <pre>
 * 성공: { "success": true,  "data": ... }
 * 실패: { "success": false, "error": { "code": "...", "message": "...", "details": { ... } } }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, Error error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new Error(code, message, null));
    }

    public static <T> ApiResponse<T> fail(String code, String message, Map<String, Object> details) {
        return new ApiResponse<>(false, null, new Error(code, message, details));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(String code, String message, Map<String, Object> details) {
    }
}
