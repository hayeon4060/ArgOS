package com.gte.argos.factory.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class RetentionServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void cutoffIsMidnightSevenDaysAgo() {
        // 시연 시계 11/16 09:00:01 → 11/09 00:00 이전만 지움 (11/09 09:00 부터인 7일치 시연 데이터는 그대로)
        Instant now = Instant.parse("2026-11-16T00:00:01Z"); // 09:00:01 KST
        assertThat(RetentionService.cutoff(now, 7, SEOUL)).isEqualTo(Instant.parse("2026-11-08T15:00:00Z"));
    }

    @Test
    void cutoffStaysSameAllDay() {
        // 같은 날 안에서는 기준이 그대로라 시연 중에 데이터 앞쪽이 깎이지 않음
        Instant morning = Instant.parse("2026-11-16T00:00:00Z"); // 09:00 KST
        Instant night = Instant.parse("2026-11-16T14:59:59Z"); // 23:59:59 KST
        assertThat(RetentionService.cutoff(morning, 7, SEOUL)).isEqualTo(RetentionService.cutoff(night, 7, SEOUL));
    }

    @Test
    void rejectsZeroDays() {
        assertThatThrownBy(() -> RetentionService.cutoff(Instant.now(), 0, SEOUL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
