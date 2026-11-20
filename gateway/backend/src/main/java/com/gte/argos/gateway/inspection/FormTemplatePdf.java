package com.gte.argos.gateway.inspection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.report.ReportService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 빈 서류 양식 PDF (GET /doc-forms/{formType}/template, docs/api/inspection-redesign-api.md 3-6).
 * 팀 양식 원본(docs/ArgOS_서류양식.pdf → resources/forms/argos-doc-forms.pdf)에서 그 양식 쪽만 잘라 준다 (2026-11-20).
 * 원본에 없는 양식이면 doc_form.field_schema 로 빈 양식을 그린다:
 * 기본 정보(info) · 측정(measure) 칸 표, 점검 항목(items) 표(항목 · 양호/불량/해당 없음 · 측정값 · 비고),
 * 이상 · 조치(issues) 표, 종합 판정(verdict) · 의견. 그 밖의 묶음은 '칸 이름 | 빈칸' 표.
 */
@Component
public class FormTemplatePdf {

    private static final Color HEAD = new Color(27, 50, 73);

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final ObjectMapper mapper;
    private final String fontPath;

    public FormTemplatePdf(NamedParameterJdbcTemplate jdbc, CurrentUser currentUser, ObjectMapper mapper,
                           @Value("${argos.report.font:}") String fontPath) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.fontPath = fontPath;
    }

    public record Template(String fileName, byte[] bytes) {
    }

    public Template build(String formType) {
        currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        Map<String, Object> form = jdbc.queryForList("SELECT * FROM doc_form WHERE form_type = :f",
                new MapSqlParameterSource("f", formType)).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FORM_NOT_FOUND", "없는 양식입니다: " + formType));
        String name = String.valueOf(form.get("name"));
        List<Map<String, Object>> fields;
        try {
            fields = mapper.readValue(String.valueOf(form.get("field_schema")), new TypeReference<>() { });
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FORM_BROKEN", "양식 정보를 읽지 못했습니다.");
        }
        try {
            byte[] page = original(formType);
            return new Template("ARG-" + formType + "_" + name + ".pdf", page != null ? page : draw(formType, name, fields));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TEMPLATE_FAILED", "양식을 만들지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 팀 양식 원본(forms/argos-doc-forms.pdf = docs/ArgOS_서류양식.pdf, 쪽마다 양식 하나)에서 'ARG-{양식}' 이 적힌 쪽만.
     * 원본이 없거나 그 양식 쪽이 없으면 null (→ 서버가 그린 양식)
     */
    static byte[] original(String formType) {
        try (java.io.InputStream in = FormTemplatePdf.class.getResourceAsStream("/forms/argos-doc-forms.pdf")) {
            if (in == null) {
                return null;
            }
            com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(in);
            com.lowagie.text.pdf.parser.PdfTextExtractor text = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            String mark = "ARG-" + formType;
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                if (text.getTextFromPage(i).replaceAll("\\s", "").contains(mark)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    Document doc = new Document(reader.getPageSizeWithRotation(i));
                    com.lowagie.text.pdf.PdfCopy copy = new com.lowagie.text.pdf.PdfCopy(doc, out);
                    doc.open();
                    copy.addPage(copy.getImportedPage(reader, i));
                    doc.close();
                    reader.close();
                    return out.toByteArray();
                }
            }
            reader.close();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] draw(String formType, String name, List<Map<String, Object>> fields) throws Exception {
        BaseFont base = font();
        Font title = new Font(base, 16, Font.BOLD);
        Font small = new Font(base, 8, Font.NORMAL, Color.GRAY);
        Font body = new Font(base, 9);
        Font head = new Font(base, 9, Font.BOLD, Color.WHITE);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Paragraph t = new Paragraph(name, title);
        t.setAlignment(Element.ALIGN_CENTER);
        doc.add(t);
        Paragraph code = new Paragraph("ARG-" + formType, small);
        code.setAlignment(Element.ALIGN_RIGHT);
        doc.add(code);
        doc.add(new Paragraph(" ", small));

        // 묶음(group) 순서대로
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> f : fields) {
            groups.computeIfAbsent(String.valueOf(f.getOrDefault("group", "info")), k -> new ArrayList<>()).add(f);
        }
        for (Map.Entry<String, List<Map<String, Object>>> g : groups.entrySet()) {
            switch (g.getKey()) {
                case "items" -> doc.add(items(g.getValue(), head, body));
                case "issues" -> doc.add(rows(g.getValue(), head, body, "이상 · 조치"));
                default -> doc.add(pairs(g.getValue(), head, body, groupName(g.getKey())));
            }
            doc.add(new Paragraph(" ", small));
        }
        doc.close();
        return out.toByteArray();
    }

    /** '칸 이름 | 빈칸' 두 쌍씩 */
    private PdfPTable pairs(List<Map<String, Object>> fs, Font head, Font body, String caption) {
        PdfPTable t = new PdfPTable(new float[] {2, 3, 2, 3});
        t.setWidthPercentage(100);
        caption(t, caption, head, 4);
        for (Map<String, Object> f : fs) {
            cell(t, label(f), body, new Color(236, 241, 246));
            cell(t, hint(f), body, null);
        }
        if (fs.size() % 2 == 1) {
            cell(t, "", body, null);
            cell(t, "", body, null);
        }
        return t;
    }

    /** 점검 항목: 같은 row 의 선택 · 측정값 · 비고를 한 줄로 */
    private PdfPTable items(List<Map<String, Object>> fs, Font head, Font body) {
        PdfPTable t = new PdfPTable(new float[] {3.2f, 4.4f, 1.6f, 2.8f});
        t.setWidthPercentage(100);
        caption(t, "점검 항목", head, 4);
        for (String h : List.of("항목", "양호 · 불량 · 해당 없음", "측정값", "비고")) {
            cell(t, h, body, new Color(236, 241, 246));
        }
        Map<Object, Map<String, Object>> byRow = new LinkedHashMap<>();
        for (Map<String, Object> f : fs) {
            Map<String, Object> row = byRow.computeIfAbsent(f.getOrDefault("row", f.get("key")), k -> new LinkedHashMap<>());
            String key = String.valueOf(f.get("key"));
            if (key.endsWith("Note")) {
                row.put("note", f);
            } else if (key.endsWith("Value")) {
                row.put("value", f);
            } else {
                row.put("main", f);
            }
        }
        for (Map<String, Object> row : byRow.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> main = (Map<String, Object>) row.get("main");
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) row.get("value");
            cell(t, main == null ? "" : String.valueOf(main.get("label")), body, null);
            cell(t, main == null ? "" : "□ 양호  □ 불량  □ 해당 없음", body, null);
            cell(t, value == null ? "" : unit(value), body, null);
            cell(t, "", body, null);
        }
        return t;
    }

    /** 이상 · 조치처럼 row 별 같은 칸이 반복되는 표 */
    private PdfPTable rows(List<Map<String, Object>> fs, Font head, Font body, String caption) {
        Map<Object, List<Map<String, Object>>> byRow = new LinkedHashMap<>();
        for (Map<String, Object> f : fs) {
            byRow.computeIfAbsent(f.getOrDefault("row", 1), k -> new ArrayList<>()).add(f);
        }
        List<Map<String, Object>> first = byRow.values().iterator().next();
        PdfPTable t = new PdfPTable(first.size());
        t.setWidthPercentage(100);
        caption(t, caption, head, first.size());
        for (Map<String, Object> f : first) {
            String l = String.valueOf(f.get("label"));
            cell(t, l.replaceFirst("^\\d+줄 ", ""), body, new Color(236, 241, 246));
        }
        for (List<Map<String, Object>> r : byRow.values()) {
            for (int i = 0; i < first.size(); i++) {
                cell(t, " ", body, null);
            }
        }
        return t;
    }

    private static void caption(PdfPTable t, String text, Font head, int span) {
        PdfPCell c = new PdfPCell(new Paragraph(text, head));
        c.setColspan(span);
        c.setBackgroundColor(HEAD);
        c.setPadding(5);
        t.addCell(c);
    }

    private static void cell(PdfPTable t, String text, Font f, Color bg) {
        PdfPCell c = new PdfPCell(new Paragraph(text, f));
        c.setPadding(5);
        c.setMinimumHeight(20);
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        t.addCell(c);
    }

    private static String label(Map<String, Object> f) {
        return String.valueOf(f.get("label"));
    }

    /** 빈칸 안내: 고르는 칸은 보기, 단위 · 접두어 */
    @SuppressWarnings("unchecked")
    private static String hint(Map<String, Object> f) {
        if (f.get("options") instanceof List<?> o) {
            return String.join("  ", ((List<Object>) o).stream().map(x -> "□ " + x).toList());
        }
        String prefix = f.get("prefix") == null ? "" : String.valueOf(f.get("prefix"));
        String unit = f.get("unit") == null ? "" : "            " + f.get("unit");
        return prefix + unit;
    }

    private static String unit(Map<String, Object> f) {
        return f.get("unit") == null ? "" : "            " + f.get("unit");
    }

    private static String groupName(String g) {
        return switch (g) {
            case "info" -> "기본 정보";
            case "measure" -> "측정";
            case "verdict" -> "종합 판정";
            default -> g;
        };
    }

    private BaseFont font() {
        List<String> tries = new ArrayList<>();
        if (fontPath != null && !fontPath.isBlank()) {
            tries.add(fontPath);
        }
        tries.addAll(ReportService.FONT_CANDIDATES);
        for (String t : tries) {
            String file = t.contains(",") ? t.substring(0, t.indexOf(',')) : t;
            if (Files.exists(Path.of(file))) {
                try {
                    return BaseFont.createFont(t, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                } catch (Exception ignored) {
                    // 다음 후보
                }
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_FONT_MISSING",
                "양식용 한글 글꼴을 찾지 못했습니다. argos.report.font 에 .ttf 경로를 넣어 주세요.");
    }
}
