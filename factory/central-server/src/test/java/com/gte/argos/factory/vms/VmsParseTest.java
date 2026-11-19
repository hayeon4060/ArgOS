package com.gte.argos.factory.vms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gte.argos.factory.common.api.ApiException;
import com.gte.argos.factory.judge.Stage;
import org.junit.jupiter.api.Test;

class VmsParseTest {

    @Test
    void 단계_숫자를_판정_단계로() {
        assertThat(VmsService.stage(1)).isEqualTo(Stage.NORMAL);
        assertThat(VmsService.stage(2)).isEqualTo(Stage.WARN);
        assertThat(VmsService.stage(3)).isEqualTo(Stage.ALARM);
        assertThat(VmsService.stage(null)).isEqualTo(Stage.NORMAL);
        assertThatThrownBy(() -> VmsService.stage(4)).isInstanceOf(ApiException.class);
    }
}
