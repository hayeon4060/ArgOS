package com.gte.argos.factory.common.time;

import java.time.LocalDate;

/**
 * 기준값 계절 (sensor_threshold.season).
 * <pre>
 * SPRING_FALL 3~5월 · 9~10월 / SUMMER 6~8월 / WINTER 11~2월 / ALL 계절 무관
 * </pre>
 * 판정은 지금 계절 줄을 먼저 쓰고, 없으면 ALL 을 쓴다.
 */
public final class Seasons {

    public static final String ALL = "ALL";
    /** ALL 을 뺀 계절 (화면 계절 탭 순서) */
    public static final java.util.List<String> SEASONS = java.util.List.of("SPRING_FALL", "SUMMER", "WINTER");

    private Seasons() {
    }

    public static String of(LocalDate date) {
        int m = date.getMonthValue();
        if (m >= 6 && m <= 8) {
            return "SUMMER";
        }
        if (m >= 11 || m <= 2) {
            return "WINTER";
        }
        return "SPRING_FALL";
    }

    public static boolean isValid(String season) {
        return ALL.equals(season) || "SPRING_FALL".equals(season) || "SUMMER".equals(season) || "WINTER".equals(season);
    }

    /** 서버 시계 기준 지금 계절 */
    public static String current(DemoClock clock) {
        return of(clock.nowOffset().toLocalDate());
    }
}
