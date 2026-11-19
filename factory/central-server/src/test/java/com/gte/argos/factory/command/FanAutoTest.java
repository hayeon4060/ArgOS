package com.gte.argos.factory.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FanAutoTest {

    @Test
    void 가스_주의는_45Hz_경보는_60Hz() {
        assertThat(FanAutoService.targetHz(2)).isEqualTo(45);
        assertThat(FanAutoService.targetHz(3)).isEqualTo(60);
        assertThat(FanAutoService.targetHz(1)).isNull();
    }

    @Test
    void 퍼센트와_회전수() {
        assertThat(FanAutoService.percent(45)).isEqualTo(75);
        assertThat(FanAutoService.percent(60)).isEqualTo(100);
        assertThat(45 * EquipmentCommandService.RPM_PER_HZ).isEqualTo(1350);
        assertThat(60 * EquipmentCommandService.RPM_PER_HZ).isEqualTo(1800);
    }
}
