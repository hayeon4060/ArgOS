package com.gte.argos.gateway.central;

/**
 * 중앙 DB 값 ↔ 화면(API 명세서) 값 변환.
 * <pre>
 * 단계     1 / 2 / 3            → NORMAL / WARN / ALARM
 * 센서     TE / IT / AT / IR    → TEMP / CURRENT / CO / IR
 * 운전     RUN / STOPPING / STOPPED → RUNNING / STOPPING / STOPPED
 * </pre>
 */
public final class Codes {

    private Codes() {
    }

    public static String level(int stage) {
        return switch (stage) {
            case 3 -> "ALARM";
            case 2 -> "WARN";
            default -> "NORMAL";
        };
    }

    /** 화면 값 → 단계 숫자 (WARN → 2). 모르면 null */
    public static Integer stage(String level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case "ALARM", "FIRE" -> 3;
            case "WARN" -> 2;
            case "NORMAL" -> 1;
            default -> null;
        };
    }

    public static String sensorType(String dbType) {
        if (dbType == null) {
            return null;
        }
        return switch (dbType) {
            case "TE" -> "TEMP";
            case "IT" -> "CURRENT";
            case "AT" -> "CO";
            default -> dbType;
        };
    }

    /** 화면 값 → DB 센서 종류 (CURRENT → IT) */
    public static String dbSensorType(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "TEMP" -> "TE";
            case "CURRENT" -> "IT";
            case "CO" -> "AT";
            default -> type;
        };
    }

    public static String runState(String dbState) {
        return "RUN".equals(dbState) ? "RUNNING" : dbState;
    }

    /** 센서 종류 한글 이름 */
    public static String sensorName(String dbType) {
        if (dbType == null) {
            return "센서";
        }
        return switch (dbType) {
            case "TE" -> "온도";
            case "IT" -> "전류";
            case "AT" -> "CO";
            case "IR" -> "열화상";
            default -> dbType;
        };
    }

    public static String stageName(int stage) {
        return stage >= 3 ? "경보" : stage == 2 ? "주의" : "정상";
    }

    /**
     * 기준 방향 (화면 '기준값 근접 센서'용).
     * UPPER = 상한만 (값이 오를수록 위험), LOWER = 하한만 (값이 내려갈수록 위험), BOTH = 둘 다 (전류처럼 양방향), 기준 없으면 null
     */
    public static String limitType(Double warn, Double alarm, Double lowWarn, Double lowAlarm) {
        boolean upper = warn != null || alarm != null;
        boolean lower = lowWarn != null || lowAlarm != null;
        if (upper && lower) {
            return "BOTH";
        }
        return upper ? "UPPER" : lower ? "LOWER" : null;
    }
}
