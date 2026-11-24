package com.gte.argos.gateway.alarm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class HourlyRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @Test
    void 날짜를_비우면_어제() {
        assertThat(AlarmService.hourlyDate(null, TODAY)).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(AlarmService.hourlyDate("yesterday", TODAY)).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(AlarmService.hourlyDate("today", TODAY)).isEqualTo(TODAY);
        assertThat(AlarmService.hourlyDate("2026-11-12", TODAY)).isEqualTo(LocalDate.of(2026, 11, 12));
    }

    @Test
    void 집계는_그날_00시부터_다음날_00시까지() {
        OffsetDateTime[] r = AlarmService.hourlyRange(LocalDate.of(2026, 11, 15));

        assertThat(r[0]).isEqualTo(OffsetDateTime.parse("2026-11-15T00:00:00+09:00"));
        assertThat(r[1]).isEqualTo(OffsetDateTime.parse("2026-11-16T00:00:00+09:00"));
    }
}
