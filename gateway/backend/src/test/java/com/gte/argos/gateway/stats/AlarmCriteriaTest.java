package com.gte.argos.gateway.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlarmCriteriaTest {

    private static final UserScope HQ = new UserScope(1, UserScope.HQ_ADMIN, null, Set.of(), Set.of(), Set.of());
    private static final LocalDate D = LocalDate.of(2026, 11, 16);

    @Test
    void 주간은_07시부터_19시_전까지() {
        assertThat(AlarmCriteria.isDayShift(OffsetDateTime.parse("2026-11-16T07:00:00+09:00"))).isTrue();
        assertThat(AlarmCriteria.isDayShift(OffsetDateTime.parse("2026-11-16T18:59:59+09:00"))).isTrue();
        assertThat(AlarmCriteria.isDayShift(OffsetDateTime.parse("2026-11-16T19:00:00+09:00"))).isFalse();
        // UTC 로 와도 한국 시각으로 본다 (22:30Z = 07:30 KST)
        assertThat(AlarmCriteria.isDayShift(OffsetDateTime.parse("2026-11-15T22:30:00Z"))).isTrue();
    }

    @Test
    void 직전_기간은_같은_길이로_바로_앞() {
        AlarmCriteria c = new AlarmCriteria(LocalDate.of(2026, 11, 10), D, null, null, null, null, null);
        AlarmCriteria p = c.previous();
        assertThat(c.days()).isEqualTo(7L);
        assertThat(p.from()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(p.to()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void 센서_종류_단계_근무조로_거른다() {
        CentralDto.Alarm warnCo = alarm(2, "AT", "2026-11-16T09:10:00+09:00");
        CentralDto.Alarm alarmTemp = alarm(3, "TE", "2026-11-16T21:10:00+09:00");

        AlarmCriteria co = new AlarmCriteria(D, D, null, null, "CO", null, null);
        assertThat(co.matches(warnCo, HQ, null)).isTrue();
        assertThat(co.matches(alarmTemp, HQ, null)).isFalse();

        AlarmCriteria alarmOnly = new AlarmCriteria(D, D, null, null, null, "ALARM", null);
        assertThat(alarmOnly.matches(warnCo, HQ, null)).isFalse();
        assertThat(alarmOnly.matches(alarmTemp, HQ, null)).isTrue();

        AlarmCriteria night = new AlarmCriteria(D, D, null, 12L, null, null, "NIGHT");
        assertThat(night.matches(warnCo, HQ, null)).isFalse();
        assertThat(night.matches(alarmTemp, HQ, null)).isTrue();
        assertThat(new AlarmCriteria(D, D, null, 13L, null, null, null).matches(alarmTemp, HQ, null)).isFalse();
    }

    private static CentralDto.Alarm alarm(int stage, String type, String at) {
        OffsetDateTime t = OffsetDateTime.parse(at);
        CentralDto.Event e = new CentralDto.Event(1, 301, type + "-301", type, "A", 41, 12, stage, "DURATION", t, null,
                10.0, 1L, 5.0, 8.0, null, null);
        return new CentralDto.Alarm(1, 5, "상도 건조로", 12, 41L, 5, 1, stage, "FIRE", "OPEN", t, null, null, null,
                null, null, false, List.of(e));
    }
}
