package com.gte.argos.gateway.alarm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte.argos.gateway.central.CentralDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmViewsTest {

    private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-16T09:02:15+09:00");

    @Test
    void 전류_저하는_미달_CO_는_초과로_제목이_나온다() {
        CentralDto.Event current = event(313, "IT", 2, 21.0, 28.05, 22.95);
        CentralDto.Event co = event(321, "AT", 2, 19.5, 15.0, null);

        assertThat(AlarmViews.title(alarm(2, List.of(current)))).isEqualTo("전류 주의 기준 미달");
        assertThat(AlarmViews.title(alarm(2, List.of(co)))).isEqualTo("CO 주의 기준 초과");
        assertThat(current.threshold()).isEqualTo(22.95);
    }

    @Test
    void 대표_이벤트는_단계가_가장_높은_것() {
        CentralDto.Event warn = event(331, "IR", 2, 55.0, 50.0, null);
        CentralDto.Event alarm = event(305, "TE", 3, 170.0, 150.0, null);

        assertThat(alarm(3, List.of(warn, alarm)).mainEvent().sensorId()).isEqualTo(305L);
        assertThat(AlarmViews.title(alarm(3, List.of(warn, alarm)))).isEqualTo("온도 경보 기준 초과");
    }

    private static CentralDto.Event event(long sensorId, String type, int stage, double peak, Double warn, Double lowWarn) {
        Double alarm = warn == null ? null : warn + 10;
        Double lowAlarm = lowWarn == null ? null : lowWarn - 5;
        return new CentralDto.Event(1, sensorId, type + "-1", type, "A", 41, 12, stage, "DURATION", T, null, peak, 1L,
                warn, alarm, lowWarn, lowAlarm);
    }

    private static CentralDto.Alarm alarm(int stage, List<CentralDto.Event> events) {
        return new CentralDto.Alarm(1, 5, "상도 건조로", 12, 41L, 5, 1, stage, "FIRE", "OPEN", T, null, null, null,
                null, null, true, events);
    }
}
