package com.gte.argos.factory.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LiveStateTest {

    private static final Instant T0 = Instant.parse("2026-11-16T00:00:00Z");

    @Test
    void 최근_1시간만_남기고_지금_값과_구간을_돌려준다() {
        LiveState state = new LiveState();
        state.record(305, T0, 140.0);
        state.record(305, T0.plusSeconds(5), 141.0);
        state.record(305, T0.plusSeconds(3600 + 10), 150.0);   // 1시간 10초 뒤 → 앞의 두 값은 지워짐

        assertThat(state.latest(305).map(LiveState.Point::value).orElse(null)).isEqualTo(150.0);
        assertThat(state.range(305, T0, T0.plusSeconds(7200))).hasSize(1);
        assertThat(state.latest(999).isPresent()).isEqualTo(false);
    }
}
