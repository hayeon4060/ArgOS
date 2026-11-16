package com.gte.argos.gateway.threshold;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThresholdAdminServiceTest {

    @Test
    void 지속_초는_수집_주기로_나눠_올림한_샘플_수가_된다() {
        assertThat(ThresholdAdminService.samples(120, 5)).isEqualTo(24);
        assertThat(ThresholdAdminService.samples(30, 5)).isEqualTo(6);
        assertThat(ThresholdAdminService.samples(7, 5)).isEqualTo(2);
        assertThat(ThresholdAdminService.samples(0, 5)).isEqualTo(1);
        assertThat(ThresholdAdminService.samples(null, 5)).isNull();
    }
}
