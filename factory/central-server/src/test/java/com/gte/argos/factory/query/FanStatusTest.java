package com.gte.argos.factory.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FanStatusTest {

    @Test
    void 명령_결과를_화면_상태로() {
        assertThat(InternalQueryRepository.fanStatus("PENDING")).isEqualTo("SENT");
        assertThat(InternalQueryRepository.fanStatus("AWAITING")).isEqualTo("AWAITING");
        assertThat(InternalQueryRepository.fanStatus("DENIED")).isEqualTo("DENIED");
        assertThat(InternalQueryRepository.fanStatus("DONE")).isEqualTo("APPLIED");
        assertThat(InternalQueryRepository.fanStatus("FAILED")).isEqualTo("FAILED");
        assertThat(InternalQueryRepository.fanStatus("EXPIRED")).isEqualTo("FAILED");
        assertThat(InternalQueryRepository.fanStatus("CANCELLED")).isEqualTo("FAILED");
    }
}
