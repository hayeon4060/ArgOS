package com.gte.argos.gateway.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WholeMonthTest {

    private static AlarmCriteria c(String from, String to) {
        return new AlarmCriteria(LocalDate.parse(from), LocalDate.parse(to), null, null, null, null, null);
    }

    @Test
    void 달_하나_전체면_31일이어도_월간() {
        assertThat(StatsService.wholeMonth(c("2026-10-01", "2026-10-31"))).isTrue();
        assertThat(StatsService.wholeMonth(c("2026-11-01", "2026-11-30"))).isTrue();
        assertThat(StatsService.wholeMonth(c("2026-02-01", "2026-02-28"))).isTrue();
    }

    @Test
    void 달을_넘거나_중간부터면_아님() {
        assertThat(StatsService.wholeMonth(c("2026-10-02", "2026-11-01"))).isFalse();
        assertThat(StatsService.wholeMonth(c("2026-10-01", "2026-10-30"))).isFalse();
        assertThat(StatsService.wholeMonth(c("2026-10-01", "2026-11-30"))).isFalse();
    }
}
