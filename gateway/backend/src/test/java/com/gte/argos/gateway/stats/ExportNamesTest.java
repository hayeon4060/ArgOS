package com.gte.argos.gateway.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte.argos.gateway.central.CentralDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportNamesTest {

    @Test
    void CSV_한글_값() {
        assertThat(StatsService.hazardName("GAS")).isEqualTo("가스");
        assertThat(StatsService.hazardName("FIRE")).isEqualTo("화재");
        assertThat(StatsService.statusName("OPEN")).isEqualTo("확인 대기");
        assertThat(StatsService.statusName("CLOSED")).isEqualTo("해제");
        assertThat(StatsService.sensorTypeName("CURRENT")).isEqualTo("전류");
    }

    @Test
    void 등급은_주의_경보_화재() {
        assertThat(StatsService.gradeName(alarm(2, "GAS"))).isEqualTo("주의");
        assertThat(StatsService.gradeName(alarm(3, "GAS"))).isEqualTo("경보");
        assertThat(StatsService.gradeName(alarm(3, "FIRE"))).isEqualTo("화재");
    }

    private static CentralDto.Alarm alarm(int stage, String hazard) {
        return new CentralDto.Alarm(1, 5, "상도 건조로", 12, 41L, 5, 1, stage, hazard, "OPEN", null, null, null, null,
                null, null, false, List.of());
    }
}
