package com.gte.argos.gateway.report;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.file.FileStore;
import com.gte.argos.gateway.inspection.InspectionService;
import com.gte.argos.gateway.stats.AlarmCriteria;
import com.gte.argos.gateway.stats.StatsService;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 월간 보고서 PDF (명세서 D1 /reports/monthly). 그 달 알림·조치·점검으로 A·B·C 안전 등급을 매기고 PDF 로 만든다.
 *
 * <p>등급 점수 = 100 − 경보 5점 × 건수 − 주의 1점 × 건수 − 점검 지연 2점 × 건수 (0~100), A ≥ 90 · B ≥ 70 · C.
 * 한글 글꼴: argos.report.font 경로, 없으면 맑은 고딕 · 나눔고딕 · Noto Sans CJK 순서로 찾는다.
 */
@Service
public class ReportService {

    public static final List<String> FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/malgun.ttf",
            "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
            "/usr/share/fonts/nanum/NanumGothic.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,1",
            "/System/Library/Fonts/AppleSDGothicNeo.ttc,0");

    private final StatsService stats;
    private final InspectionService inspections;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final NamedParameterJdbcTemplate jdbc;
    private final FileStore files;
    private final ActivityLogger activity;
    private final SimTime time;
    private final String fontPath;

    public ReportService(StatsService stats, InspectionService inspections, MasterCache masterCache,
                         CurrentUser currentUser, NamedParameterJdbcTemplate jdbc, FileStore files,
                         ActivityLogger activity, SimTime time, @Value("${argos.report.font:}") String fontPath) {
        this.stats = stats;
        this.inspections = inspections;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
        this.files = files;
        this.activity = activity;
        this.time = time;
        this.fontPath = fontPath;
    }

    public record Report(String fileName, byte[] body) {
    }

    record Grade(char grade, double score) {
    }

    static Grade grade(int alarms, int warns, int delayed) {
        double score = Math.max(0, Math.min(100, 100 - 5.0 * alarms - warns - 2.0 * delayed));
        return new Grade(score >= 90 ? 'A' : score >= 70 ? 'B' : 'C', score);
    }

    @SuppressWarnings("unchecked")
    public Report monthly(long factoryId, String month) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        currentUser.checkFactory(scope, factoryId);
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "month 는 2026-10 형식입니다.");
        }
        if (ym.isAfter(YearMonth.from(time.now()))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_READY", "그 달 등급이 아직 계산되지 않았습니다.");
        }
        MasterCache.Snapshot m = masterCache.get();
        CentralDto.Factory factory = m.factory(factoryId);
        CentralDto.Plant plant = factory == null ? null : m.plant(factory.plantId());
        AlarmCriteria c = new AlarmCriteria(ym.atDay(1), ym.atEndOfMonth(), factoryId, null, null, null, null);
        Map<String, Object> summary = stats.summary(c);
        List<Map<String, Object>> byProcess = stats.byProcess(c);
        Map<String, Object> insp = inspections.summary(month, factoryId);
        int alarmCount = value(summary.get("alarmCount"));
        int danger = value(summary.get("dangerCount"));
        int delayed = ((Number) insp.get("delayed")).intValue();
        Grade g = grade(danger, alarmCount - danger, delayed);
        jdbc.update("""
                INSERT INTO safety_grade (factory_id, month, grade, score) VALUES (:f, :m, :g, :s)
                ON CONFLICT (factory_id, month) DO UPDATE SET grade = EXCLUDED.grade, score = EXCLUDED.score
                """, new MapSqlParameterSource("f", factoryId).addValue("m", ym.atDay(1))
                .addValue("g", String.valueOf(g.grade())).addValue("s", g.score()));

        byte[] pdf = pdf(plant == null ? "" : plant.name(), factory == null ? "공장 " + factoryId : factory.name(), ym, g,
                alarmCount, danger, summary, insp, byProcess);
        String code = (plant == null ? "site" : plant.code()) + (factory == null ? factoryId : factory.code());
        String name = "ArgOS_report_" + code.toLowerCase().replaceAll("[^a-z0-9]", "") + "_"
                + month.replace("-", "") + ".pdf";
        files.save("REPORT", name, "application/pdf", pdf);
        activity.log(scope.userId(), "EXPORT", plant == null ? null : plant.id(), null, "report", null,
                "월간 보고서 내려받기 " + (factory == null ? factoryId : factory.name()) + " " + month);
        return new Report(name, pdf);
    }

    private byte[] pdf(String plantName, String factoryName, YearMonth ym, Grade g, int alarmCount, int danger,
                       Map<String, Object> summary, Map<String, Object> insp, List<Map<String, Object>> byProcess) {
        try {
            return build(plantName, factoryName, ym, g, alarmCount, danger, summary, insp, byProcess);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "REPORT_FAILED", "보고서를 만들지 못했습니다: " + e.getMessage());
        }
    }

    private byte[] build(String plantName, String factoryName, YearMonth ym, Grade g, int alarmCount, int danger,
                         Map<String, Object> summary, Map<String, Object> insp, List<Map<String, Object>> byProcess)
            throws Exception {
        BaseFont base = font();
        Font title = new Font(base, 20, Font.BOLD);
        Font h2 = new Font(base, 13, Font.BOLD);
        Font body = new Font(base, 10);
        Font big = new Font(base, 40, Font.BOLD, gradeColor(g.grade()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 48, 48, 56, 56);
        PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new Paragraph("ArgOS 월간 안전 보고서", title));
        doc.add(new Paragraph(plantName + " · " + factoryName + " · " + ym.getYear() + "년 " + ym.getMonthValue() + "월", body));
        doc.add(new Paragraph(" ", body));

        PdfPTable gradeBox = new PdfPTable(new float[] {1, 3});
        gradeBox.setWidthPercentage(100);
        PdfPCell gc = new PdfPCell(new Paragraph(String.valueOf(g.grade()), big));
        gc.setHorizontalAlignment(Element.ALIGN_CENTER);
        gc.setVerticalAlignment(Element.ALIGN_MIDDLE);
        gc.setPadding(12);
        gradeBox.addCell(gc);
        PdfPCell gt = new PdfPCell(new Paragraph("안전 등급 " + g.grade() + " (" + Math.round(g.score()) + "점)\n"
                + "점수 = 100 − 경보 5점 × 건수 − 주의 1점 × 건수 − 점검 지연 2점 × 건수\nA 90점 이상 · B 70점 이상 · C 그 아래", body));
        gt.setPadding(10);
        gradeBox.addCell(gt);
        doc.add(gradeBox);

        doc.add(new Paragraph(" ", body));
        doc.add(new Paragraph("1. 요약", h2));
        PdfPTable s = table(base, List.of("항목", "값"), new float[] {2, 3});
        row(s, body, "알림 건수", alarmCount + "건 (경보 " + danger + "건)");
        row(s, body, "평균 조치 시간", text(metric(summary.get("avgActionMinutes")), "분"));
        row(s, body, "체크리스트 완료율", text(metric(summary.get("checklistDoneRate")), "%"));
        row(s, body, "점검 이행률", insp.get("rate") + "% (완료 " + insp.get("done") + " · 예정 " + insp.get("planned")
                + " · 지연 " + insp.get("delayed") + ")");
        doc.add(s);

        doc.add(new Paragraph(" ", body));
        doc.add(new Paragraph("2. 공정별 알림", h2));
        PdfPTable p = table(base, List.of("공정", "전류", "온도", "CO", "열화상", "합계"), new float[] {3, 1, 1, 1, 1, 1});
        if (byProcess.isEmpty()) {
            PdfPCell none = new PdfPCell(new Paragraph("알림 없음", body));
            none.setColspan(6);
            p.addCell(none);
        }
        for (Map<String, Object> r : byProcess) {
            row(p, body, String.valueOf(r.get("processName")), String.valueOf(r.get("CURRENT")), String.valueOf(r.get("TEMP")),
                    String.valueOf(r.get("CO")), String.valueOf(r.get("IR")), String.valueOf(r.get("total")));
        }
        doc.add(p);

        doc.add(new Paragraph(" ", body));
        doc.add(new Paragraph("3. 점검 서류", h2));
        PdfPTable f = table(base, List.of("양식", "완료/전체", "검수 대기"), new float[] {3, 1, 1});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forms = (List<Map<String, Object>>) insp.get("byForm");
        for (Map<String, Object> r : forms) {
            row(f, body, r.get("formType") + " " + r.get("name"), r.get("done") + "/" + r.get("total"), String.valueOf(r.get("review")));
        }
        doc.add(f);
        doc.add(new Paragraph(" ", body));
        doc.add(new Paragraph("만든 시각: " + time.now().atZoneSameInstant(SimTime.ZONE).toLocalDateTime().withNano(0)
                + " (시연 시계 기준)", body));
        doc.close();
        return out.toByteArray();
    }

    private BaseFont font() {
        List<String> tries = new ArrayList<>();
        if (fontPath != null && !fontPath.isBlank()) {
            tries.add(fontPath);
        }
        tries.addAll(FONT_CANDIDATES);
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
                "보고서용 한글 글꼴을 찾지 못했습니다. argos.report.font 에 .ttf 경로를 넣어 주세요.");
    }

    private static PdfPTable table(BaseFont base, List<String> header, float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        Font hf = new Font(base, 10, Font.BOLD, Color.WHITE);
        for (String h : header) {
            PdfPCell c = new PdfPCell(new Paragraph(h, hf));
            c.setBackgroundColor(new Color(27, 50, 73));
            c.setPadding(5);
            t.addCell(c);
        }
        return t;
    }

    private static void row(PdfPTable t, Font f, String... cells) {
        for (String v : cells) {
            PdfPCell c = new PdfPCell(new Paragraph(v == null || "null".equals(v) ? "-" : v, f));
            c.setPadding(5);
            t.addCell(c);
        }
    }

    private static Color gradeColor(char g) {
        return g == 'A' ? new Color(15, 123, 108) : g == 'B' ? new Color(196, 101, 26) : new Color(201, 74, 54);
    }

    @SuppressWarnings("unchecked")
    private static Object metric(Object m) {
        return m instanceof Map<?, ?> map ? ((Map<String, Object>) map).get("value") : m;
    }

    private static int value(Object m) {
        Object v = metric(m);
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static String text(Object v, String unit) {
        return v == null ? "-" : v + unit;
    }
}
