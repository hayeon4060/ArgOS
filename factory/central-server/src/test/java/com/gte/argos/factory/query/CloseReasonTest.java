package com.gte.argos.factory.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CloseReasonTest {

    @Test
    void 조치_중에_닫으면_ACTION_DONE_아니면_MANUAL() {
        assertThat(AlarmStatusService.closeReason("ACTING")).isEqualTo("ACTION_DONE");
        assertThat(AlarmStatusService.closeReason("ACKED")).isEqualTo("MANUAL");
        assertThat(AlarmStatusService.closeReason("OPEN")).isEqualTo("MANUAL");
    }
}
