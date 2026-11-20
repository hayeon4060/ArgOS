package com.gte.argos.gateway.inspection;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * 네이버 CLOVA OCR (템플릿 OCR 권장). 템플릿의 필드 이름을 양식 칸 key(workDate, co …)와 같게 만들어 두면
 * 이름으로 칸을 채운다. 이름이 없는 일반 OCR 결과면 칸은 빈 값 + 확인 필요로 둔다.
 *
 * <pre>
 * POST {invokeUrl}  X-OCR-SECRET: {secret}
 * { "version": "V2", "requestId": "...", "timestamp": 0, "images": [ { "format": "png", "name": "doc", "data": base64 } ] }
 * → images[0].fields[]: { name, inferText, inferConfidence, boundingPoly.vertices[{x,y}] }, images[0].convertedImageInfo{width,height}
 * </pre>
 */
public class ClovaOcrEngine implements OcrEngine {

    static final double MIN_CONFIDENCE = 0.9;

    private final RestClient rest;
    private final String url;
    private final String secret;

    public ClovaOcrEngine(String url, String secret) {
        this.rest = RestClient.create();
        this.url = url;
        this.secret = secret;
    }

    @Override
    public String name() {
        return "CLOVA";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result recognize(byte[] file, String mime, String fileName, Form form) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("format", format(mime, fileName));
        image.put("name", "doc");
        image.put("data", Base64.getEncoder().encodeToString(file));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", "V2");
        body.put("requestId", UUID.randomUUID().toString());
        body.put("timestamp", System.currentTimeMillis());
        body.put("images", List.of(image));
        Map<String, Object> res = rest.post().uri(url).header("X-OCR-SECRET", secret)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
        List<Map<String, Object>> images = res == null ? List.of() : (List<Map<String, Object>>) res.getOrDefault("images", List.of());
        Map<String, Object> first = images.isEmpty() ? Map.of() : images.get(0);
        return toResult((List<Map<String, Object>>) first.getOrDefault("fields", List.of()),
                (Map<String, Object>) first.getOrDefault("convertedImageInfo", Map.of()), form);
    }

    /** CLOVA 응답 → 양식 칸 (테스트하기 쉽게 static) */
    @SuppressWarnings("unchecked")
    static Result toResult(List<Map<String, Object>> fields, Map<String, Object> imageInfo, Form form) {
        double w = num(imageInfo.get("width"), 1);
        double h = num(imageInfo.get("height"), 1);
        List<Map<String, Object>> out = new ArrayList<>();
        double sum = 0;
        int n = 0;
        for (Map<String, Object> f : form.fields()) {
            String key = String.valueOf(f.get("key"));
            Map<String, Object> hit = null;
            for (Map<String, Object> c : fields) {
                if (key.equals(c.get("name"))) {
                    hit = c;
                    break;
                }
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("key", key);
            v.put("label", f.get("label"));
            if (hit == null) {
                v.put("value", "");
                v.put("box", null);
                v.put("needCheck", true);
                v.put("reason", "인식되지 않은 칸");
            } else {
                String text = String.valueOf(hit.getOrDefault("inferText", "")).strip();
                double conf = num(hit.get("inferConfidence"), 0);
                sum += conf;
                n++;
                v.put("value", text);
                v.put("box", box((Map<String, Object>) hit.get("boundingPoly"), w, h));
                String reason = null;
                if (conf < MIN_CONFIDENCE) {
                    reason = "인식 신뢰도가 낮음 (" + Math.round(conf * 100) + "%)";
                } else if ("number".equals(f.get("type")) && !text.matches("-?\\d+(\\.\\d+)?")) {
                    reason = "숫자가 아님";
                } else if (f.get("limit") instanceof Number lim && text.matches("-?\\d+(\\.\\d+)?")
                        && Double.parseDouble(text) > lim.doubleValue()) {
                    reason = "기준(" + lim + ") 초과";
                }
                v.put("needCheck", reason != null);
                if (reason != null) {
                    v.put("reason", reason);
                }
            }
            out.add(v);
        }
        return new Result(out, n == 0 ? 0 : sum / n);
    }

    @SuppressWarnings("unchecked")
    private static List<Double> box(Map<String, Object> poly, double w, double h) {
        if (poly == null || !(poly.get("vertices") instanceof List<?> vs) || vs.isEmpty()) {
            return null;
        }
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = 0, maxY = 0;
        for (Object o : vs) {
            Map<String, Object> p = (Map<String, Object>) o;
            double x = num(p.get("x"), 0);
            double y = num(p.get("y"), 0);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return List.of(r(minX / w), r(minY / h), r((maxX - minX) / w), r((maxY - minY) / h));
    }

    private static String format(String mime, String name) {
        String n = name == null ? "" : name.toLowerCase();
        if ((mime != null && mime.contains("pdf")) || n.endsWith(".pdf")) {
            return "pdf";
        }
        if ((mime != null && mime.contains("png")) || n.endsWith(".png")) {
            return "png";
        }
        return "jpg";
    }

    private static double num(Object o, double d) {
        return o instanceof Number x ? x.doubleValue() : d;
    }

    private static double r(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
