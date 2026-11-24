package com.gte.argos.gateway.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BandMaxTest {

    @Test
    void 기준_띠_끝은_경보_주의_지금값_중_큰_값의_125퍼센트() {
        assertThat(ThresholdAdminService.bandMax(Map.of("warn", 150.0, "alarm", 160.0), 140.0)).isEqualTo(200.0);
        assertThat(ThresholdAdminService.bandMax(Map.of("warn", 15.0, "alarm", 30.0), 40.0)).isEqualTo(50.0);
        assertThat(ThresholdAdminService.bandMax(Map.of(), null)).isNull();
    }
}
