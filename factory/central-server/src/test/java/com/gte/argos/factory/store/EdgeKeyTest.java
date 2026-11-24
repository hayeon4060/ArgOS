package com.gte.argos.factory.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class EdgeKeyTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void 공장코드_태그_시작시각_모양이다() {
        String key = JdbcJudgementStore.edgeKey("P5-AT-301", Instant.parse("2026-11-17T08:55:25Z"), SEOUL);

        assertThat(key).isEqualTo("P5-AT-301-20261117T175525");
    }

    @Test
    void 길면_36자로_자른다() {
        String key = JdbcJudgementStore.edgeKey("FACTORY001-VERY-LONG-TAG-NAME", Instant.parse("2026-11-17T08:55:25Z"), SEOUL);

        assertThat(key).hasSize(36);
    }
}
