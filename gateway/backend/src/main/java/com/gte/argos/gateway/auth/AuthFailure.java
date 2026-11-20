package com.gte.argos.gateway.auth;

import java.util.Map;

public class AuthFailure extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, Object> details;

    public AuthFailure(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public AuthFailure(int status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public int status() { return status; }
    public String code() { return code; }
    public Map<String, Object> details() { return details; }
}
