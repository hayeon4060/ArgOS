package com.gte.argos.factory.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandStatusTest {

    @Test
    void 처리_중은_SENT_나머지는_결과_그대로() {
        assertThat(EquipmentCommandService.status("PENDING")).isEqualTo("SENT");
        assertThat(EquipmentCommandService.status("DONE")).isEqualTo("DONE");
        assertThat(EquipmentCommandService.status("EXPIRED")).isEqualTo("EXPIRED");
    }
}
