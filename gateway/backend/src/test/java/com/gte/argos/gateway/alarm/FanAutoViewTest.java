package com.gte.argos.gateway.alarm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte.argos.gateway.central.CentralDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FanAutoViewTest {

    private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-16T09:00:00+09:00");

    @Test
    void 가스_알림만_fanAuto_를_보여준다() {
        CentralDto.FanAuto f = new CentralDto.FanAuto(41L, 42, "상도 건조로 배기팬", "AUTO", 75, 1350, 1200, "APPLIED", T);
        Map<String, Object> v = AlarmViews.fanAuto(alarm("GAS", f));
        assertThat(v).containsEntry("percent", 75).containsEntry("rpm", 1350).containsEntry("beforeRpm", 1200)
                .containsEntry("status", "APPLIED").containsEntry("equipmentId", 41L);
        assertThat(AlarmViews.fanAuto(alarm("FIRE", f))).isNull();
        assertThat(AlarmViews.fanAuto(alarm("GAS", null))).isNull();
    }

    @Test
    void 상세_패널_단계_이름() {
        assertThat(AlarmService.historyStep("REPORT")).isEqualTo("REPORTED");
        assertThat(AlarmService.historyStep("CLOSE")).isEqualTo("CLOSED");
        assertThat(AlarmService.historyStep("SHUTDOWN")).isEqualTo("SHUTDOWN");
    }

    private static CentralDto.Alarm alarm(String hazard, CentralDto.FanAuto f) {
        return new CentralDto.Alarm(1, 5, "상도 건조로", 12, 41L, 5, 1, 2, hazard, "OPEN", T, null, null, null,
                null, null, false, List.of(), f);
    }
}
