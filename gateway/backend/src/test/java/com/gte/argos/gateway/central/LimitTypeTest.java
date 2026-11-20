package com.gte.argos.gateway.central;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LimitTypeTest {

    @Test
    void 상한_하한_둘다_없음() {
        assertThat(Codes.limitType(150.0, 160.0, null, null)).isEqualTo("UPPER");
        assertThat(Codes.limitType(null, null, 16.2, 12.6)).isEqualTo("LOWER");
        assertThat(Codes.limitType(19.8, 22.5, 16.2, 12.6)).isEqualTo("BOTH");
        assertThat(Codes.limitType(null, null, null, null)).isNull();
    }
}
