package com.gte.argos.gateway.common.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 공통 응답 모양 (API 명세서 '공통 규칙')
 * <pre>
 * 성공: { "success": true,  "data": ... }
 * 실패: { "success": false, "error": { "code": "...", "message": "...", ...추가 정보 } }
 * </pre>
 * 실패의 추가 정보는 명세서대로 error 바로 아래에 펼친다 (예: error.remaining, error.sensors).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, Error error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new Error(code, message, Map.of()));
    }

    public static <T> ApiResponse<T> fail(String code, String message, Map<String, Object> details) {
        return new ApiResponse<>(false, null, new Error(code, message, details == null ? Map.of() : details));
    }

    public record Error(String code, String message, @com.fasterxml.jackson.annotation.JsonIgnore Map<String, Object> details) {

        @JsonAnyGetter
        public Map<String, Object> extra() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (details != null) {
                details.forEach((k, v) -> {
                    if (!"code".equals(k) && !"message".equals(k)) {
                        m.put(k, v);
                    }
                });
            }
            return m;
        }
    }
}
