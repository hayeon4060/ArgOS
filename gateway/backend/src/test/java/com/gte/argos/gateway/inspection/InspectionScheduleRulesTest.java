package com.gte.argos.gateway.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gte.argos.gateway.common.api.ApiException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionScheduleRulesTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    @Test
    void 조회_범위는_하루_한달_기간() {
        assertThat(InspectionScheduleService.range(d("2026-11-19"), null, null, null))
                .containsExactly(d("2026-11-19"), d("2026-11-19"));
        assertThat(InspectionScheduleService.range(null, "2026-10", null, null))
                .containsExactly(d("2026-10-01"), d("2026-10-31"));
        assertThat(InspectionScheduleService.range(null, null, d("2026-11-14"), d("2026-11-20")))
                .containsExactly(d("2026-11-14"), d("2026-11-20"));
        assertThatThrownBy(() -> InspectionScheduleService.range(null, null, null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> InspectionScheduleService.range(null, null, d("2026-01-01"), d("2026-12-31")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RANGE_TOO_LARGE"));
    }

    @Test
    void 반복_날짜() {
        assertThat(InspectionScheduleService.dates(d("2026-11-24"), "WEEKLY", 3))
                .containsExactly(d("2026-11-24"), d("2026-12-01"), d("2026-12-08"));
        assertThat(InspectionScheduleService.dates(d("2026-01-31"), "MONTHLY", 2))
                .containsExactly(d("2026-01-31"), d("2026-02-28"));
        assertThat(InspectionScheduleService.dates(d("2026-11-24"), "ONCE", 1)).containsExactly(d("2026-11-24"));
    }

    @Test
    void 설비번호_숫자() {
        assertThat(InspectionScheduleService.number("EF-07")).isEqualTo(7);
        assertThat(InspectionScheduleService.number("07")).isEqualTo(7);
        assertThat(InspectionScheduleService.number("설비")).isNull();
    }

    @Test
    void 결과는_불량이_있으면_NG() {
        assertThat(InspectionScheduleService.result(null, Map.of("item01", "양호", "item04", "불량"))).isEqualTo("NG");
        assertThat(InspectionScheduleService.result(null, Map.of("item01", "양호"))).isEqualTo("OK");
        assertThat(InspectionScheduleService.result("OK", Map.of("item04", "불량"))).isEqualTo("OK");
        assertThatThrownBy(() -> InspectionScheduleService.result("BAD", Map.of())).isInstanceOf(ApiException.class);
    }

    @Test
    void 인식_칸_값과_점검_일시() {
        Map<String, Object> v = InspectionScheduleService.values(List.of(
                Map.of("key", "inspectedAt", "value", "2026-11-19T09:10"),
                Map.of("key", "equipmentNo", "value", "EF-07")));
        assertThat(v).containsEntry("equipmentNo", "EF-07");
        assertThat(InspectionScheduleService.inspectedDate(v)).isEqualTo(d("2026-11-19"));
        assertThat(InspectionScheduleService.inspectedTime(v)).isEqualTo(LocalTime.of(9, 10));
        assertThat(InspectionScheduleService.inspectedDate(Map.of("checkDate", "2026.11.18"))).isEqualTo(d("2026-11-18"));
    }

    @Test
    void 공정을_모르는_스캔본은_설비번호가_맞는_담당_공정으로() {
        // 담당 공정 하나 (시연: 도장 12, 설비 8대) → 번호가 맞든 아니든 그 공정
        assertThat(InspectionScheduleService.pickProcess(3, Map.of(12L, 8))).isEqualTo(12L);
        assertThat(InspectionScheduleService.pickProcess(null, Map.of(12L, 8))).isEqualTo(12L);
        // 여러 공정: EF-07 은 설비가 7대 이상인 공정이 하나뿐이면 그 공정
        Map<Long, Integer> many = new java.util.LinkedHashMap<>();
        many.put(11L, 4);
        many.put(12L, 8);
        assertThat(InspectionScheduleService.pickProcess(7, many)).isEqualTo(12L);
        // 둘 다 맞으면 모름
        assertThat(InspectionScheduleService.pickProcess(3, many)).isNull();
        assertThat(InspectionScheduleService.pickProcess(3, Map.of())).isNull();
    }
}
