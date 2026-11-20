package com.gte.argos.gateway.fire;

import static org.assertj.core.api.Assertions.assertThat;

import com.gte.argos.gateway.central.CentralDto;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FireRuleTest {

    private static final OffsetDateTime T = OffsetDateTime.parse("2026-11-16T09:10:00+09:00");

    @Test
    void 화재_위험_3단계에_열화상_이벤트가_있으면_화재() {
        CentralDto.Alarm a = alarm("FIRE", 3, "OPEN", ev("TE", 3, 172.0, 160.0), ev("IR", 3, 486.0, 150.0));
        assertThat(FireService.isFire(a)).isTrue();
        assertThat(FireService.irEvent(a).peak()).isEqualTo(486.0);
    }

    @Test
    void 열화상이_없거나_2단계거나_가스거나_종료면_화재가_아님() {
        assertThat(FireService.isFire(alarm("FIRE", 3, "OPEN", ev("TE", 3, 172.0, 160.0)))).isFalse();
        assertThat(FireService.isFire(alarm("FIRE", 2, "OPEN", ev("IR", 2, 120.0, 150.0)))).isFalse();
        assertThat(FireService.isFire(alarm("GAS", 3, "OPEN", ev("IR", 3, 486.0, 150.0)))).isFalse();
        assertThat(FireService.isFire(alarm("FIRE", 3, "CLOSED", ev("IR", 3, 486.0, 150.0)))).isFalse();
    }

    @Test
    void 판정_근거는_열화상_기준과_함께_오른_센서() {
        String basis = FireService.basis(alarm("FIRE", 3, "OPEN", ev("IR", 3, 486.0, 150.0), ev("TE", 3, 212.0, 160.0)));
        assertThat(basis).isEqualTo("열화상 최고 온도가 화재 기준(150 ℃)을 넘었습니다. 온도 212 ℃ 동시 상승");
    }

    @Test
    void 다섯_번_틀리면_잠김() {
        assertThat(FireService.unlock(4).get("locked")).isEqualTo(false);
        assertThat(FireService.unlock(5).get("locked")).isEqualTo(true);
    }

    private static CentralDto.Event ev(String type, int stage, Double peak, Double alarmThr) {
        return new CentralDto.Event(1, 331, type + "-301", type, "℃", 41, 12, stage, "HI", T, null,
                peak, 1L, null, alarmThr, null, null);
    }

    private static CentralDto.Alarm alarm(String hazard, int stage, String status, CentralDto.Event... events) {
        return new CentralDto.Alarm(1051, 5, "상도 건조로", 12, 41L, 5, 1, stage, hazard, status, T, null, null,
                null, null, null, false, List.of(events));
    }
}
