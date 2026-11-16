package com.gte.argos.factory.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte.argos.factory.judge.ThresholdRule;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThresholdSimulationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-15T09:00:00+09:00");

    @Test
    void 주의_2분_지속이면_3분_넘은_구간은_1건_1분짜리는_0건() {
        List<ThresholdService.Minute> m = new ArrayList<>();
        add(m, 0, 10, 140, 141);
        add(m, 10, 3, 140, 152);   // 3분 동안 최대가 150 넘음 → 주의 1건
        add(m, 13, 10, 140, 141);
        add(m, 23, 1, 140, 155);   // 1분만 → 안 셈
        add(m, 24, 5, 140, 141);
        ThresholdRule rule = new ThresholdRule(1, 150.0, 160.0, null, null, 24, 6); // 2분 / 30초

        ThresholdService.Count c = ThresholdService.count(m, rule, 5);

        assertThat(c.warn()).isEqualTo(1);
        assertThat(c.alarm()).isEqualTo(0);
        assertThat(c.hits()).containsExactly(T0.plusMinutes(10));
    }

    @Test
    void 기준을_낮추면_건수가_늘고_경보_범위가_있으면_경보로_센다() {
        List<ThresholdService.Minute> m = new ArrayList<>();
        add(m, 0, 3, 140, 152);
        add(m, 3, 2, 140, 141);
        add(m, 5, 3, 140, 165);    // 경보 범위 3분 → 경보 1건
        ThresholdRule rule = new ThresholdRule(1, 150.0, 160.0, null, null, 24, 6);

        ThresholdService.Count c = ThresholdService.count(m, rule, 5);

        assertThat(c.warn()).isEqualTo(1);
        assertThat(c.alarm()).isEqualTo(1);
    }

    @Test
    void 전류_하한도_최소값으로_센다() {
        List<ThresholdService.Minute> m = new ArrayList<>();
        add(m, 0, 4, 19.0, 22.0); // 최소 19.0 ≤ 하한 주의 19.8
        ThresholdRule rule = new ThresholdRule(1, 24.2, 27.5, 19.8, 15.4, 24, 6);

        assertThat(ThresholdService.count(m, rule, 5).warn()).isEqualTo(1);
    }

    private static void add(List<ThresholdService.Minute> m, int startMin, int count, double min, double max) {
        for (int i = 0; i < count; i++) {
            m.add(new ThresholdService.Minute(T0.plusMinutes(startMin + i), min, max));
        }
    }
}
