package com.gte.argos.gateway.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormV2Test {

    @Test
    void 고르는_칸은_보기_중_하나_시각_칸은_HH_MM() {
        Map<String, Object> form = Map.of("fields", List.of(
                Map.of("key", "item01", "type", "choice", "options", List.of("양호", "불량", "해당 없음")),
                Map.of("key", "startTime", "type", "time")));
        assertThat(InspectionService.validate(List.of(v("item01", "양호"), v("startTime", "09:30")), form)).isEmpty();
        assertThat(InspectionService.validate(List.of(v("item01", "좋음"), v("startTime", "9시")), form))
                .containsExactly("item01", "startTime");
    }

    @Test
    void 시연용_인식기는_F01_예시_원본_값을_채운다() {
        SampleOcrEngine engine = new SampleOcrEngine("2026-11-16");
        OcrEngine.Form form = new OcrEngine.Form("F01", "밀폐공간 작업허가서", List.of(
                Map.of("key", "co1030", "type", "number", "limit", 30),
                Map.of("key", "pre1", "type", "check"),
                Map.of("key", "workPeriod", "type", "text")));
        List<Map<String, Object>> f = engine.recognize(new byte[0], "image/jpeg", "f01.jpg", form).fields();
        assertThat(f.get(0)).containsEntry("value", "7").containsEntry("needCheck", true);
        assertThat(f.get(1)).containsEntry("value", "예").containsEntry("needCheck", false);
        assertThat(f.get(2)).containsEntry("value", "11-16 09:00 ~ 12:00");
    }

    @Test
    void 다른_양식은_칸_종류대로_값을_채운다() {
        SampleOcrEngine engine = new SampleOcrEngine("2026-11-16");
        OcrEngine.Form form = new OcrEngine.Form("F02", "환기·배기설비 점검일지", List.of(
                Map.of("key", "item02", "type", "choice", "options", List.of("양호", "불량")),
                Map.of("key", "checkedAt", "type", "datetime"),
                Map.of("key", "inspectorSign", "type", "sign")));
        List<Map<String, Object>> f = engine.recognize(new byte[0], "image/jpeg", "f02.jpg", form).fields();
        assertThat(f.get(0)).containsEntry("value", "양호");
        assertThat(f.get(1)).containsEntry("value", "2026-11-16 09:00");
    }

    private static Map<String, Object> v(String key, String value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("value", value);
        return m;
    }
}
