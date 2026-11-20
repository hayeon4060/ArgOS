package com.gte.argos.gateway.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionRulesTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-16T12:00:00+09:00");

    @Test
    void 일정_상태는_완료_지연_예정() {
        assertThat(InspectionService.scheduleStatus(row("DONE", "2026-11-16", "08:30:00"), NOW)).isEqualTo("DONE");
        assertThat(InspectionService.scheduleStatus(row("OVERDUE", "2026-11-14", null), NOW)).isEqualTo("DELAYED");
        assertThat(InspectionService.scheduleStatus(row("PLANNED", "2026-11-16", "10:00:00"), NOW)).isEqualTo("DELAYED");
        assertThat(InspectionService.scheduleStatus(row("PLANNED", "2026-11-16", "15:00:00"), NOW)).isEqualTo("PLANNED");
        assertThat(InspectionService.scheduleStatus(row("PLANNED", "2026-11-15", null), NOW)).isEqualTo("DELAYED");
    }

    @Test
    void 확인한_칸은_확인_필요가_꺼지고_값이_바뀜() {
        List<Map<String, Object>> fields = List.of(
                field("co", "1", true), field("workDate", "2026-11-12", false));
        List<Map<String, Object>> merged = InspectionService.merge(fields,
                List.of(Map.of("key", "co", "value", "7", "checked", true)));
        assertThat(merged.get(0).get("value")).isEqualTo("7");
        assertThat(merged.get(0).get("needCheck")).isEqualTo(false);
        assertThat(merged.get(1).get("value")).isEqualTo("2026-11-12");
    }

    @Test
    void 날짜와_숫자_형식이_틀린_칸을_찾음() {
        Map<String, Object> form = Map.of("fields", List.of(
                Map.of("key", "co", "type", "number"), Map.of("key", "workDate", "type", "date"),
                Map.of("key", "place", "type", "text")));
        List<String> bad = InspectionService.validate(List.of(field("co", "7ppm", false),
                field("workDate", "11/12", false), field("place", "아무거나", false)), form);
        assertThat(bad).isEqualTo(List.of("co", "workDate"));
    }

    @Test
    void 파일_종류와_양식_짐작() {
        assertThat(InspectionService.fileKind("a.PDF", null)).isEqualTo("application/pdf");
        assertThat(InspectionService.fileKind("a.hwp", "application/x-hwp")).isNull();
        assertThat(InspectionService.guessForm("밀폐공간작업허가서_1112.pdf")).isEqualTo("F01");
        assertThat(InspectionService.guessForm("scan.png")).isEqualTo("F02");
    }

    @Test
    void 클로바_결과를_양식_칸에_맞춤() {
        OcrEngine.Form form = new OcrEngine.Form("F01", "밀폐공간 작업허가서", List.of(
                Map.of("key", "co", "label", "CO 농도", "type", "number", "limit", 30),
                Map.of("key", "place", "label", "작업 장소", "type", "text")));
        OcrEngine.Result r = ClovaOcrEngine.toResult(List.of(Map.of("name", "co", "inferText", "45",
                "inferConfidence", 0.99, "boundingPoly", Map.of("vertices", List.of(
                        Map.of("x", 100, "y", 50), Map.of("x", 200, "y", 50), Map.of("x", 200, "y", 70), Map.of("x", 100, "y", 70))))),
                Map.of("width", 1000, "height", 500), form);
        assertThat(r.fields().get(0).get("needCheck")).isEqualTo(true);
        assertThat(r.fields().get(0).get("box")).isEqualTo(List.of(0.1, 0.1, 0.1, 0.04));
        assertThat(r.fields().get(1).get("needCheck")).isEqualTo(true);
    }

    private static Map<String, Object> row(String status, String day, String time) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", status);
        r.put("planned_at", day);
        r.put("planned_time", time);
        return r;
    }

    private static Map<String, Object> field(String key, String value, boolean needCheck) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("value", value);
        f.put("needCheck", needCheck);
        return f;
    }
}
