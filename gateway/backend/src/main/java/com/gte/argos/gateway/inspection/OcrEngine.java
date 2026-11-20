package com.gte.argos.gateway.inspection;

import java.util.List;
import java.util.Map;

/** 서류 글자 인식. CLOVA OCR 설정이 있으면 {@link ClovaOcrEngine}, 없으면 {@link SampleOcrEngine}(시연용) */
public interface OcrEngine {

    /**
     * @param form 양식 (formType, name, fields 정의)
     * @return 칸마다 { key, label, value, box[x,y,w,h](0~1), needCheck, reason? }
     */
    Result recognize(byte[] file, String mime, String fileName, Form form);

    String name();

    record Form(String formType, String name, List<Map<String, Object>> fields) {
    }

    record Result(List<Map<String, Object>> fields, double confidence) {
    }
}
