package com.gte.argos.gateway.common.api;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 상태 코드·오류 코드·추가 정보를 담아 던지는 예외. ApiExceptionHandler 가 { success:false, error } 로 바꾼다 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final transient Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    public static ApiException outOfScope() {
        return forbidden("OUT_OF_SCOPE", "담당 범위가 아닙니다.");
    }

    public static ApiException actionNotAllowed() {
        return forbidden("ACTION_NOT_ALLOWED", "이 권한으로는 할 수 없는 동작입니다.");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
