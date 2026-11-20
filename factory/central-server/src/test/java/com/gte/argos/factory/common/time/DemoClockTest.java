package com.gte.argos.factory.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DemoClockTest {

    private static final Instant SIM_START = Instant.parse("2026-11-16T00:00:00Z"); // 09:00 KST

    @Test
    void 지금은_실제_서버_시각이다() {
        MutableClock real = new MutableClock(Instant.parse("2026-11-20T11:00:00Z")); // 20:00 KST
        DemoClock clock = new DemoClock(SIM_START, ZoneId.of("Asia/Seoul"), real);

        assertThat(clock.now()).isEqualTo(real.instant());

        real.advance(Duration.ofMinutes(3));
        assertThat(clock.now()).isEqualTo(Instant.parse("2026-11-20T11:03:00Z"));
        assertThat(clock.nowOffset().getHour()).isEqualTo(20);
        assertThat(clock.nowOffset().getMinute()).isEqualTo(3);
    }

    @Test
    void 리셋해도_실제_시각_그대로이고_더미_경계는_유지된다() {
        MutableClock real = new MutableClock(Instant.parse("2026-11-20T11:00:00Z"));
        DemoClock clock = new DemoClock(SIM_START, ZoneId.of("Asia/Seoul"), real);
        real.advance(Duration.ofMinutes(10));

        clock.restart();

        assertThat(clock.now()).isEqualTo(Instant.parse("2026-11-20T11:10:00Z"));
        assertThat(clock.simStart()).isEqualTo(SIM_START);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
