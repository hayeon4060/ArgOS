package com.gte.argos.gateway.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.equipment.EquipmentActionService.FanCommand;
import org.junit.jupiter.api.Test;

class FanCommandTest {

    @Test
    void 끄기는_FAN_STOP_켜기는_평상시_30Hz() {
        assertThat(EquipmentActionService.fanCommand("OFF", null, null))
                .isEqualTo(new FanCommand("FAN_STOP", null, "끄기"));
        assertThat(EquipmentActionService.fanCommand("ON", null, null).hz()).isEqualTo(30);
    }

    @Test
    void 회전수는_30으로_나눈_주파수() {
        assertThat(EquipmentActionService.fanCommand(null, 1200, null).hz()).isEqualTo(40);
        assertThat(EquipmentActionService.fanCommand(null, 1500, null).hz()).isEqualTo(50);
        assertThat(EquipmentActionService.fanCommand(null, 1800, null).hz()).isEqualTo(60);
        assertThat(EquipmentActionService.fanCommand(null, 1210, null).hz()).isEqualTo(40);
    }

    @Test
    void 범위_밖이면_400() {
        assertThatThrownBy(() -> EquipmentActionService.fanCommand(null, 500, null))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "RPM_OUT_OF_RANGE");
        assertThatThrownBy(() -> EquipmentActionService.fanCommand(null, null, 70))
                .isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("code", "HZ_OUT_OF_RANGE");
        assertThatThrownBy(() -> EquipmentActionService.fanCommand(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
