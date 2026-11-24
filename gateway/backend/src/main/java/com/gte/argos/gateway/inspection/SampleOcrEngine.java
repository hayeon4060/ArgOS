package com.gte.argos.gateway.inspection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CLOVA OCR 연결 전 시연용 인식기. 양식 칸마다 그럴듯한 값을 채우고, 숫자 칸 하나는 '확인 필요'로 표시한다.
 * 실제 글자를 읽지 않는다 (README 에 명시).
 */
public class SampleOcrEngine implements OcrEngine {

    private final String today;

    public SampleOcrEngine(String today) {
        this.today = today;
    }

    @Override
    public String name() {
        return "SAMPLE";
    }

    @Override
    public Result recognize(byte[] file, String mime, String fileName, Form form) {
        List<Map<String, Object>> out = new ArrayList<>();
        boolean flagged = false;
        int i = 0;
        for (Map<String, Object> f : form.fields()) {
            String key = String.valueOf(f.get("key"));
            String type = String.valueOf(f.get("type"));
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("key", key);
            v.put("label", f.get("label"));
            String[] f01 = "F01".equals(form.formType()) ? F01.get(key) : null;
            v.put("value", f01 != null ? f01[0].replace("{today}", today).replace("{md}", today.substring(5))
                    : sample(key, type, f));
            v.put("box", f01 != null && f01[1] != null ? box(f01[1])
                    : List.of(0.52, round(Math.min(0.95, 0.12 + (i % 60) * 0.014)), 0.22, 0.02));
            // 확인 필요: F01 은 예시 원본 그대로(작업자 · 10:30 CO), 나머지는 첫 숫자 칸 하나
            boolean check = f01 != null ? f01.length > 2 : !flagged && "number".equals(type);
            v.put("needCheck", check);
            if (check) {
                v.put("reason", f01 != null ? f01[2] : "숫자 1과 7이 비슷하게 쓰임");
                flagged = true;
            }
            out.add(v);
            i++;
        }
        return new Result(out, 0.86);
    }

    /**
     * F01 예시 원본(밀폐공간 작업허가서 f01_filled.jpg, 910×1286)에 맞춘 값: { 값, "x,y,w,h"(픽셀) 또는 null, 확인 필요 사유 }.
     * 프론트 예시 데이터(api/mock/inspectionData.js f01Fields)와 같은 값
     */
    private static final Map<String, String[]> F01 = Map.ofEntries(
            Map.entry("applyDate", new String[] {"{today}", "660,200,170,26"}),
            Map.entry("place", new String[] {"5공장 도장 공정 · 전처리 탈지 탱크 내부", "150,300,470,26"}),
            Map.entry("equipmentNo", new String[] {"PT-01", "645,232,100,26"}),
            Map.entry("workPeriod", new String[] {"{md} 09:00 ~ 12:00", "150,332,580,26"}),
            Map.entry("manager", new String[] {"박성훈", "195,416,90,26"}),
            Map.entry("supervisor", new String[] {"이재호", "195,450,90,26"}),
            Map.entry("workers", new String[] {"최민규, 정우진", "165,483,225,26", "글자가 흐립니다"}),
            Map.entry("co0850", new String[] {"4", "132,814,650,28"}),
            Map.entry("co0930", new String[] {"6", "132,851,650,28"}),
            Map.entry("co1030", new String[] {"7", "420,887,40,30", "숫자 1과 7이 비슷하게 쓰임"}),
            Map.entry("endAt", new String[] {"{today} 11:50", "230,984,230,26"}),
            Map.entry("exitCheck", new String[] {"전원 퇴출 확인", "680,984,170,26"}),
            Map.entry("remark", new String[] {"이상 없음", null}),
            Map.entry("signer", new String[] {"박성훈", "158,1091,330,26"}));

    private static List<Double> box(String px) {
        String[] p = px.split(",");
        double[] wh = {910, 1286, 910, 1286};
        List<Double> out = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            out.add(Math.round(Double.parseDouble(p[k]) / wh[k] * 10000) / 10000.0);
        }
        return out;
    }

    private String sample(String key, String type, Map<String, Object> field) {
        if (key.startsWith("pre")) {
            return "예";
        }
        switch (type) {
            case "date" -> {
                return today;
            }
            case "datetime" -> {
                return today + " 09:00";
            }
            case "time" -> {
                return "09:00";
            }
            case "check" -> {
                return "예";
            }
            case "sign" -> {
                return "김도현";
            }
            case "choice" -> {
                Object o = field.get("options");
                return o instanceof List<?> l && !l.isEmpty() ? String.valueOf(l.get(0)) : "양호";
            }
            case "number" -> {
                if (field.get("limit") instanceof Number n) {
                    return String.valueOf(Math.max(1, Math.round(n.doubleValue() * 0.2)));
                }
                if (field.get("min") instanceof Number n) {
                    return String.valueOf(n.intValue());
                }
            }
            default -> {
            }
        }
        return switch (key) {
            case "o2" -> "20.8";
            case "co" -> "7";
            case "lel" -> "3";
            case "fanCurrent" -> "25.1";
            case "setTemp" -> "180";
            case "measuredTemp" -> "182";
            case "extinguisherCount" -> "6";
            case "place", "location" -> "도장 공정 상도 건조로";
            case "equipment", "oven" -> "상도 건조로";
            case "damper" -> "정상";
            case "pressureOk" -> "정상";
            default -> "number".equals(type) ? "0" : "김도현";
        };
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
