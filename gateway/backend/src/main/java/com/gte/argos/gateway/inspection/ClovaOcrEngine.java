package com.gte.argos.gateway.inspection;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                String raw = String.valueOf(hit.getOrDefault("inferText", "")).strip();
                double conf = num(hit.get("inferConfidence"), 0);
                sum += conf;
                n++;
                String type = String.valueOf(f.get("type"));
                String text = normalize(type, raw);
                v.put("value", text == null ? raw : text);
                v.put("box", box((Map<String, Object>) hit.get("boundingPoly"), w, h));
                String reason = null;
                if (CHECK_TYPES.contains(type) || CHECK_MARKS.matcher(raw).find()) {
                    // 체크 표시(V · ☑)는 글자 인식으로 믿을 수 없어서 값은 비우고 사람이 원본을 보고 고른다
                    v.put("value", "");
                    reason = "체크 칸은 원본을 보고 직접 선택";
                } else if (raw.isEmpty()) {
                    // 비어 있는 칸 (비고 · 이상 사항 빈 줄 등). 신뢰도 0 이 와도 확인 필요로 보지 않는다
                    reason = null;
                } else if (text == null) {
                    reason = "date".equals(type) ? "날짜 형식 확인 (YYYY-MM-DD)"
                            : "datetime".equals(type) ? "일시 형식 확인 (YYYY-MM-DD HH:mm)" : "시각 형식 확인 (HH:mm)";
                } else if (conf < MIN_CONFIDENCE) {
                    reason = "인식 신뢰도가 낮음 (" + Math.round(conf * 100) + "%)";
                } else if ("number".equals(type) && !text.isEmpty() && !text.matches("-?\\d+(\\.\\d+)?")) {
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

    /** 글자로 믿을 수 없는 칸 (선택지 체크 · 예/아니오 체크) */
    static final Set<String> CHECK_TYPES = Set.of("choice", "check");

    /** 인쇄된 체크 상자가 같이 읽힌 칸 (예: 글자 칸인 공장 · 구역이 종이에서는 □울산 □아산 □전주) */
    private static final Pattern CHECK_MARKS = Pattern.compile("[□☐☑☒■✓✔]");

    private static final Pattern DATE = Pattern.compile("(\\d{4}|\\d{2})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2})\\s*[:시]\\s*(\\d{1,2})");

    /**
     * 읽은 글자를 칸 형식에 맞게 바꾼다. 양식에 인쇄된 모양 그대로 읽히기 때문
     * (예: "26 . 11 . 27" → 2026-11-27, "2026 . 11 . 20 09 : 10" → 2026-11-20 09:10, "1,200 " → 1200).
     * 날짜 · 일시 · 시각인데 바꿀 수 없으면 null (빈 글자는 빈 글자 그대로)
     */
    static String normalize(String type, String raw) {
        String t = raw == null ? "" : raw.strip();
        if (t.isEmpty()) {
            return "";
        }
        switch (type) {
            case "number" -> {
                return t.replaceAll("[\\s,]", "");
            }
            case "date" -> {
                return date(t);
            }
            case "datetime" -> {
                Matcher d = DATE.matcher(t);
                if (!d.find()) {
                    return null;
                }
                String day = date(d.group());
                String time = time(t.substring(d.end()));
                return day == null || time == null ? null : day + " " + time;
            }
            case "time" -> {
                return time(t);
            }
            default -> {
                return t;
            }
        }
    }

    private static String date(String t) {
        Matcher m = DATE.matcher(t);
        if (!m.find()) {
            return null;
        }
        int y = Integer.parseInt(m.group(1));
        try {
            return LocalDate.of(y < 100 ? 2000 + y : y, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)))
                    .toString();
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static String time(String t) {
        Matcher m = TIME.matcher(t);
        if (!m.find()) {
            return null;
        }
        int hh = Integer.parseInt(m.group(1));
        int mm = Integer.parseInt(m.group(2));
        return hh > 23 || mm > 59 ? null : String.format("%02d:%02d", hh, mm);
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
