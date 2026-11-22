package com.gte.argos.gateway.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** CLOVA 로 읽은 글자를 F02 칸 형식으로 바꾸기 (날짜 · 일시 · 숫자 · 체크 칸) */
class ClovaNormalizeTest {

    @Test
    void 날짜는_양식_모양에서_YYYY_MM_DD_로() {
        assertThat(ClovaOcrEngine.normalize("date", "26 . 11 . 27")).isEqualTo("2026-11-27");
        assertThat(ClovaOcrEngine.normalize("date", "2026. 11. 12")).isEqualTo("2026-11-12");
        assertThat(ClovaOcrEngine.normalize("date", "2026-1-5")).isEqualTo("2026-01-05");
        assertThat(ClovaOcrEngine.normalize("date", "2026. 13. 40")).isNull();
        assertThat(ClovaOcrEngine.normalize("date", "내일")).isNull();
        assertThat(ClovaOcrEngine.normalize("date", " ")).isEmpty();
    }

    @Test
    void 일시와_시각() {
        assertThat(ClovaOcrEngine.normalize("datetime", "2026 . 11 . 20 09 : 10")).isEqualTo("2026-11-20 09:10");
        assertThat(ClovaOcrEngine.normalize("datetime", "2026 . 11 . 20")).isNull();
        assertThat(ClovaOcrEngine.normalize("time", "9 : 05")).isEqualTo("09:05");
        assertThat(ClovaOcrEngine.normalize("time", "25:00")).isNull();
    }

    @Test
    void 숫자는_공백과_쉼표를_뺌() {
        assertThat(ClovaOcrEngine.normalize("number", "1,200 ")).isEqualTo("1200");
        assertThat(ClovaOcrEngine.normalize("number", "20 .8")).isEqualTo("20.8");
        assertThat(ClovaOcrEngine.normalize("text", " 김도현 ")).isEqualTo("김도현");
    }

    @Test
    void 체크_칸은_비우고_확인_필요_날짜는_바꿔서_채움() {
        OcrEngine.Form form = new OcrEngine.Form("F02", "환기·배기설비 점검일지", List.of(
                Map.of("key", "item01", "label", "1. 배기팬 가동 상태", "type", "choice",
                        "options", List.of("양호", "불량", "해당 없음")),
                Map.of("key", "issue1Due", "label", "1줄 조치 기한", "type", "date"),
                Map.of("key", "inspectedAt", "label", "점검 일자·시각", "type", "datetime"),
                Map.of("key", "item02Value", "label", "모터 운전 전류 측정값", "type", "number")));
        OcrEngine.Result r = ClovaOcrEngine.toResult(List.of(
                Map.of("name", "item01", "inferText", "☑양호 □불량 □해당 없음", "inferConfidence", 0.99),
                Map.of("name", "issue1Due", "inferText", "26 . 11 . 27", "inferConfidence", 0.98),
                Map.of("name", "inspectedAt", "inferText", "2026 . 11 . 20 감사", "inferConfidence", 0.98),
                Map.of("name", "item02Value", "inferText", "12. 4", "inferConfidence", 0.97)),
                Map.of("width", 1000, "height", 1000), form);

        Map<String, Object> check = r.fields().get(0);
        assertThat(check.get("value")).isEqualTo("");
        assertThat(check.get("needCheck")).isEqualTo(true);
        assertThat(check.get("reason")).isEqualTo("체크 칸은 원본을 보고 직접 선택");

        Map<String, Object> due = r.fields().get(1);
        assertThat(due.get("value")).isEqualTo("2026-11-27");
        assertThat(due.get("needCheck")).isEqualTo(false);

        Map<String, Object> at = r.fields().get(2);
        assertThat(at.get("value")).isEqualTo("2026 . 11 . 20 감사");
        assertThat(at.get("needCheck")).isEqualTo(true);

        Map<String, Object> amp = r.fields().get(3);
        assertThat(amp.get("value")).isEqualTo("12.4");
        assertThat(amp.get("needCheck")).isEqualTo(false);
    }

    @Test
    void CLOVA_양식_목록() {
        assertThat(InspectionService.parseForms(" f02, F03 ,")).containsExactly("F02", "F03");
        assertThat(InspectionService.parseForms("")).isEmpty();
    }
}
