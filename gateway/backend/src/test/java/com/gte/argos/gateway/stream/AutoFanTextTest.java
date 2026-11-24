package com.gte.argos.gateway.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutoFanTextTest {

    @Test
    void 자동_제어_이용_로그_문구() {
        assertThat(FeedPoller.autoFanText("상도 건조로 배기팬", 45, 2)).isEqualTo("상도 건조로 배기팬 45Hz · 1350rpm (가스 주의 자동)");
        assertThat(FeedPoller.autoFanText("상도 건조로 배기팬", 60, 3)).isEqualTo("상도 건조로 배기팬 60Hz · 1800rpm (가스 경보 자동)");
    }
}
