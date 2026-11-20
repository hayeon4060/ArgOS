package com.gte.argos.gateway.inspection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.file.FileStore;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 점검 관리 · 수기 서류 등록 (명세서 E1) 과 오늘 점검 (B3).
 * 서류를 올리면 파일을 저장하고 202 를 돌려준 뒤, 뒤에서 글자 인식(OCR)을 해 status 를 REVIEW 로 바꾼다.
 * CLOVA OCR 설정(argos.ocr.clova.url · secret)이 없으면 시연용 인식기가 그럴듯한 값을 채운다.
 */
@Service
public class InspectionService {

    static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final MasterCache masterCache;
    private final FileStore files;
    private final ActivityLogger activity;
    private final ObjectMapper mapper;
    private final SimTime time;
    private final OcrEngine ocr;
    private final InspectionScheduleService schedules;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ocr");
        t.setDaemon(true);
        return t;
    });

    public InspectionService(NamedParameterJdbcTemplate jdbc, CurrentUser currentUser, MasterCache masterCache,
                             FileStore files, ActivityLogger activity, ObjectMapper mapper, SimTime time,
                             InspectionScheduleService schedules,
                             @Value("${argos.ocr.clova.url:}") String clovaUrl,
                             @Value("${argos.ocr.clova.secret:}") String clovaSecret) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.masterCache = masterCache;
        this.files = files;
        this.activity = activity;
        this.mapper = mapper;
        this.time = time;
        this.schedules = schedules;
        this.ocr = clovaUrl.isBlank() || clovaSecret.isBlank()
                ? new SampleOcrEngine(LocalDate.now().toString()) : new ClovaOcrEngine(clovaUrl, clovaSecret);
        log.info("서류 인식기: {}", ocr.name());
    }

    // ---------------------------------------------------------------- 오늘 점검 (B3)

    public Map<String, Object> today(long processId) {
        UserScope scope = currentUser.scope();
        currentUser.checkProcess(scope, processId);
        OffsetDateTime now = time.now();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.*, u.name AS assignee FROM inspection_schedule s LEFT JOIN app_user u ON u.user_id = s.assignee_id
                WHERE s.process_id = :p AND s.planned_at = :day ORDER BY s.planned_time NULLS LAST, s.schedule_id
                """, new MapSqlParameterSource("p", processId).addValue("day", now.toLocalDate()));
        MasterCache.Snapshot m = masterCache.get();
        List<Map<String, Object>> items = new ArrayList<>();
        int done = 0;
        for (Map<String, Object> r : rows) {
            String status = scheduleStatus(r, now);
            if ("DONE".equals(status)) {
                done++;
            }
            CentralDto.Equipment eq = m.equipment(((Number) r.get("equipment_id")).longValue());
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("scheduleId", r.get("schedule_id"));
            v.put("time", r.get("planned_time") == null ? null : r.get("planned_time").toString().substring(0, 5));
            v.put("equipmentName", eq == null ? null : eq.name());
            v.put("formType", r.get("form_type"));
            v.put("title", r.get("title"));
            v.put("assignee", r.get("assignee"));
            v.put("status", status);
            items.add(v);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("doneCount", done);
        out.put("totalCount", rows.size());
        out.put("items", items);
        return out;
    }

    /**
     * GET /inspections/schedule?date= — 달력 날짜를 눌렀을 때 그날 점검 목록 (명세서에 없음, backend-notes 13장).
     * 담당 범위 안 공정만. 시각순.
     */
    public List<Map<String, Object>> daySchedule(java.time.LocalDate date, Long factoryId) {
        UserScope scope = admin();
        if (factoryId != null) {
            currentUser.checkFactory(scope, factoryId);
        }
        OffsetDateTime now = time.now();
        MasterCache.Snapshot m = masterCache.get();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT s.*, u.name AS assignee, d.name AS form_name, du.name AS done_by_name
                FROM inspection_schedule s
                LEFT JOIN app_user u ON u.user_id = s.assignee_id
                LEFT JOIN app_user du ON du.user_id = s.done_by
                LEFT JOIN doc_form d ON d.form_type = s.form_type
                WHERE s.planned_at = :day ORDER BY s.planned_time NULLS LAST, s.schedule_id
                """, new MapSqlParameterSource("day", date))) {
            Long pid = r.get("process_id") == null ? null : ((Number) r.get("process_id")).longValue();
            if (!inScope(scope, pid, factoryId, m)) {
                continue;
            }
            CentralDto.Equipment eq = m.equipment(((Number) r.get("equipment_id")).longValue());
            CentralDto.Process p = pid == null ? null : m.process(pid);
            CentralDto.Factory f = p == null ? null : m.factory(p.factoryId());
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("scheduleId", r.get("schedule_id"));
            v.put("date", date.toString());
            v.put("time", r.get("planned_time") == null ? null : r.get("planned_time").toString().substring(0, 5));
            v.put("title", r.get("title"));
            v.put("formType", r.get("form_type"));
            v.put("formName", r.get("form_name"));
            v.put("processId", pid);
            v.put("processName", (f == null ? "" : f.name() + " ") + (p == null ? "" : p.name()));
            v.put("equipmentId", eq == null ? null : eq.id());
            v.put("equipmentName", eq == null ? null : eq.name());
            v.put("assignee", r.get("assignee"));
            v.put("status", scheduleStatus(r, now));
            v.put("doneAt", r.get("done_at"));
            v.put("doneBy", r.get("done_by_name"));
            out.add(v);
        }
        return out;
    }

    /** DONE | DELAYED | PLANNED (예정 시각이 지났는데 안 했으면 DELAYED) */
    static String scheduleStatus(Map<String, Object> r, OffsetDateTime now) {
        String s = String.valueOf(r.get("status"));
        if ("DONE".equals(s)) {
            return "DONE";
        }
        if ("OVERDUE".equals(s)) {
            return "DELAYED";
        }
        LocalDate day = toDate(r.get("planned_at"));
        Object t = r.get("planned_time");
        LocalTime time = t == null ? LocalTime.MAX : LocalTime.parse(t.toString().length() > 8 ? t.toString().substring(0, 8) : t.toString());
        LocalDate today = now.atZoneSameInstant(SimTime.ZONE).toLocalDate();
        if (day.isBefore(today) || (day.isEqual(today) && time.isBefore(now.atZoneSameInstant(SimTime.ZONE).toLocalTime()))) {
            return "DELAYED";
        }
        return "PLANNED";
    }

    // ---------------------------------------------------------------- E1

    public Map<String, Object> summary(String month, Long factoryId) {
        UserScope scope = admin();
        if (factoryId != null) {
            currentUser.checkFactory(scope, factoryId);
        }
        YearMonth ym = month == null || month.isBlank() ? YearMonth.from(time.now()) : YearMonth.parse(month);
        OffsetDateTime now = time.now();
        MasterCache.Snapshot m = masterCache.get();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.* FROM inspection_schedule s WHERE s.planned_at >= :from AND s.planned_at < :to ORDER BY s.planned_at
                """, new MapSqlParameterSource("from", ym.atDay(1)).addValue("to", ym.plusMonths(1).atDay(1)));
        int done = 0, planned = 0, delayed = 0;
        Map<String, int[]> cal = new TreeMap<>();
        Map<String, int[]> byForm = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Long pid = r.get("process_id") == null ? null : ((Number) r.get("process_id")).longValue();
            if (!inScope(scope, pid, factoryId, m)) {
                continue;
            }
            String st = scheduleStatus(r, now);
            int[] c = cal.computeIfAbsent(toDate(r.get("planned_at")).toString(), k -> new int[3]);
            int[] f = byForm.computeIfAbsent(String.valueOf(r.get("form_type")), k -> new int[3]);
            f[1]++;
            switch (st) {
                case "DONE" -> { done++; c[0]++; f[0]++; }
                case "DELAYED" -> { delayed++; c[2]++; }
                default -> { planned++; c[1]++; }
            }
        }
        Map<String, Integer> review = new LinkedHashMap<>();
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT form_type, COUNT(*) AS n FROM ocr_result WHERE status = 'REVIEW'
                  AND uploaded_at >= :from AND uploaded_at < :to GROUP BY form_type
                """, new MapSqlParameterSource("from", ym.atDay(1).atStartOfDay(SimTime.ZONE).toOffsetDateTime())
                .addValue("to", ym.plusMonths(1).atDay(1).atStartOfDay(SimTime.ZONE).toOffsetDateTime()))) {
            review.put(String.valueOf(r.get("form_type")), ((Number) r.get("n")).intValue());
        }
        List<Map<String, Object>> forms = new ArrayList<>();
        for (Map<String, Object> df : jdbc.queryForList("SELECT form_type, name FROM doc_form ORDER BY form_type",
                new MapSqlParameterSource())) {
            String ft = String.valueOf(df.get("form_type"));
            int[] f = byForm.getOrDefault(ft, new int[3]);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("formType", ft);
            v.put("name", df.get("name"));
            v.put("done", f[0]);
            v.put("total", f[1]);
            v.put("review", review.getOrDefault(ft, 0));
            forms.add(v);
        }
        List<Map<String, Object>> calendar = new ArrayList<>();
        cal.forEach((d, c) -> calendar.add(Map.of("date", d, "done", c[0], "planned", c[1], "delayed", c[2])));
        int total = done + planned + delayed;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", ym.toString());
        out.put("rate", total == 0 ? 0 : Math.round(done * 100.0 / total));
        out.put("done", done);
        out.put("planned", planned);
        out.put("delayed", delayed);
        out.put("byForm", forms);
        out.put("calendar", calendar);
        return out;
    }

    public List<Map<String, Object>> forms() {
        admin();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : jdbc.queryForList("SELECT * FROM doc_form ORDER BY form_type", new MapSqlParameterSource())) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("formType", r.get("form_type"));
            v.put("name", r.get("name"));
            v.put("fields", json(r.get("field_schema")));
            out.add(v);
        }
        return out;
    }

    /** POST /inspections (multipart) → 202 */
    public Map<String, Object> upload(byte[] bytes, String fileName, String mime, String formType, Long scheduleId,
                                      Long processId) {
        UserScope scope = admin();
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "파일이 없습니다.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.valueOf(413), "FILE_TOO_LARGE", "20MB 이하 파일만 올릴 수 있습니다.");
        }
        String kind = fileKind(fileName, mime);
        if (kind == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE_NOT_ALLOWED", "PDF · JPG · PNG 파일만 올릴 수 있습니다.");
        }
        if (processId != null) {
            currentUser.checkProcess(scope, processId);
        }
        Map<String, Object> schedule = null;
        if (scheduleId != null) {
            schedule = jdbc.queryForList("SELECT * FROM inspection_schedule WHERE schedule_id = :id",
                    new MapSqlParameterSource("id", scheduleId)).stream().findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "점검 일정이 없습니다."));
            if (processId == null && schedule.get("process_id") != null) {
                processId = ((Number) schedule.get("process_id")).longValue();
            }
            if (formType == null && schedule.get("form_type") != null) {
                formType = String.valueOf(schedule.get("form_type"));
            }
        }
        String ft = formType == null || formType.isBlank() ? guessForm(fileName) : formType;
        form(ft); // 없는 양식이면 400
        FileStore.Stored f = files.save("DOC_SCAN", fileName, kind, bytes);
        Long id = jdbc.queryForObject("""
                INSERT INTO ocr_result (schedule_id, file_id, form_type, status, uploaded_by, uploaded_at, process_id)
                VALUES (:schedule, :file, :form, 'RECOGNIZING', :by, :at, :process) RETURNING ocr_id
                """, new MapSqlParameterSource("schedule", scheduleId).addValue("file", f.id()).addValue("form", ft)
                .addValue("by", scope.userId()).addValue("at", time.now()).addValue("process", processId), Long.class);
        startOcr(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("fileName", fileName);
        out.put("status", "RECOGNIZING");
        return out;
    }

    public Map<String, Object> ocrResult(long id) {
        UserScope scope = admin();
        Map<String, Object> r = row(id);
        String status = String.valueOf(r.get("status"));
        if ("FAILED".equals(status)) {
            throw new ApiException(HttpStatus.valueOf(422), "OCR_FAILED", "글자를 읽지 못했습니다. 다시 스캔하거나 직접 입력하세요.");
        }
        List<Map<String, Object>> fields = fields(r);
        int need = needCheckCount(fields);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", status);
        out.put("progress", "RECOGNIZING".equals(status) || "UPLOADED".equals(status) ? 50 : 100);
        out.put("formType", r.get("form_type"));
        out.put("pageCount", 1);
        out.put("fields", fields);
        out.put("needCheckCount", need);
        out.put("engine", ocr.name());
        // 이 스캔본에 맞는 점검 건 추천 (점검 관리 개편 3-7). 이미 이어졌으면 그 건, 없으면 null
        out.put("scheduleId", "REVIEW".equals(status) || "DONE".equals(status) ? schedules.recommend(r, fields, scope)
                : r.get("schedule_id"));
        return out;
    }

    public FileStore.Stored file(long id) {
        admin();
        Map<String, Object> r = row(id);
        return files.find(((Number) r.get("file_id")).longValue());
    }

    public byte[] read(FileStore.Stored f) {
        return files.read(f);
    }

    /** PUT /inspections/{id}: REVIEW(임시 저장) | DONE(검수 완료) */
    public Map<String, Object> save(long id, String status, List<Map<String, Object>> changes) {
        return save(id, status, changes, false, null);
    }

    /**
     * @param link       scheduleId 를 보냈는지 (3-7). 보냈으면 검수 완료 때 그 건(숫자) 또는 새 건(null)에 잇는다
     * @param scheduleId 이을 점검 건
     */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> save(long id, String status, List<Map<String, Object>> changes, boolean link,
                                    Long scheduleId) {
        UserScope scope = admin();
        Map<String, Object> r = row(id);
        if (!"REVIEW".equals(status) && !"DONE".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "status 는 REVIEW 나 DONE 입니다.");
        }
        Map<String, Object> form = form(String.valueOf(r.get("form_type")));
        List<Map<String, Object>> fields = merge(fields(r), changes == null ? List.of() : changes);
        List<String> bad = validate(fields, form);
        if (!bad.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "날짜·숫자 형식을 확인하세요.",
                    Map.of("fields", bad));
        }
        if ("DONE".equals(status)) {
            for (Map<String, Object> f : fields) {
                if (Boolean.TRUE.equals(f.get("needCheck"))) {
                    throw new ApiException(HttpStatus.CONFLICT, "NEED_CHECK_REMAINS", "확인 필요 칸이 남아 있습니다.");
                }
            }
        }
        OffsetDateTime now = time.now();
        jdbc.update("""
                UPDATE ocr_result SET fields = CAST(:fields AS JSONB), status = :status,
                       reviewed_by = CASE WHEN :status = 'DONE' THEN :by ELSE reviewed_by END,
                       reviewed_at = CASE WHEN :status = 'DONE' THEN CAST(:now AS TIMESTAMPTZ) ELSE reviewed_at END
                WHERE ocr_id = :id
                """, new MapSqlParameterSource("fields", write(fields)).addValue("status", status)
                .addValue("by", scope.userId()).addValue("now", now).addValue("id", id));
        Long linked = r.get("schedule_id") == null ? null : ((Number) r.get("schedule_id")).longValue();
        String reviewer = null;
        if ("DONE".equals(status)) {
            if (link || linked != null) {
                // 점검 건을 완료 · 수기로, 결과는 인식 값에 '불량'이 있으면 NG (연결 안 함이면 새 건)
                linked = schedules.linkScan(id, r, fields, link ? scheduleId : linked, scope);
            }
            reviewer = jdbc.queryForObject("SELECT name FROM app_user WHERE user_id = :id",
                    new MapSqlParameterSource("id", scope.userId()), String.class);
            activity.log(scope.userId(), "INSPECTION", scope.siteId(),
                    r.get("process_id") == null ? null : ((Number) r.get("process_id")).longValue(), "ocr_result", id,
                    "점검 서류 검수 완료 (" + form.get("name") + ")");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", status);
        out.put("reviewedBy", reviewer);
        out.put("reviewedAt", "DONE".equals(status) ? now : null);
        out.put("scheduleId", linked);
        return out;
    }

    /** POST /inspections/{id}/ocr: 고친 값을 지우고 다시 인식 */
    public Map<String, Object> rerun(long id) {
        admin();
        row(id);
        jdbc.update("UPDATE ocr_result SET fields = NULL, confidence = NULL, status = 'RECOGNIZING' WHERE ocr_id = :id",
                new MapSqlParameterSource("id", id));
        startOcr(id);
        return Map.of("id", id, "status", "RECOGNIZING");
    }

    public Map<String, Object> list(LocalDate from, LocalDate to, String formType, String status, int page, int size) {
        UserScope scope = admin();
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (from != null) {
            where.append(" AND o.uploaded_at >= :from");
            p.addValue("from", from.atStartOfDay(SimTime.ZONE).toOffsetDateTime());
        }
        if (to != null) {
            where.append(" AND o.uploaded_at < :to");
            p.addValue("to", to.plusDays(1).atStartOfDay(SimTime.ZONE).toOffsetDateTime());
        }
        if (formType != null && !formType.isBlank()) {
            where.append(" AND o.form_type = :form");
            p.addValue("form", formType);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND o.status = :status");
            p.addValue("status", status);
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.ocr_id, o.form_type, d.name AS form_name, f.orig_name, o.process_id, u.name AS uploaded_by,
                       o.uploaded_at, o.status, o.fields, o.reviewed_at
                FROM ocr_result o JOIN file_asset f ON f.file_id = o.file_id
                LEFT JOIN doc_form d ON d.form_type = o.form_type
                LEFT JOIN app_user u ON u.user_id = o.uploaded_by""" + where + " ORDER BY o.uploaded_at DESC, o.ocr_id DESC", p);
        MasterCache.Snapshot m = masterCache.get();
        List<Map<String, Object>> visible = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Long pid = r.get("process_id") == null ? null : ((Number) r.get("process_id")).longValue();
            if (!inScope(scope, pid, null, m)) {
                continue;
            }
            CentralDto.Process proc = pid == null ? null : m.process(pid);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", r.get("ocr_id"));
            v.put("formType", r.get("form_type"));
            v.put("formName", r.get("form_name"));
            v.put("fileName", r.get("orig_name"));
            v.put("processName", proc == null ? null : proc.name());
            v.put("uploadedBy", r.get("uploaded_by"));
            v.put("uploadedAt", zoned(r.get("uploaded_at")));
            v.put("status", r.get("status"));
            // 목록에서도 확인 필요 칸 수 · 진행 · 저장 시각 (GET /inspections/{id}/ocr 와 같은 값, frontend-api-audit-1120 6장)
            String st = String.valueOf(r.get("status"));
            v.put("needCheckCount", needCheckCount(fields(r)));
            v.put("progress", "RECOGNIZING".equals(st) || "UPLOADED".equals(st) ? 50 : 100);
            v.put("reviewedAt", zoned(r.get("reviewed_at")));
            visible.add(v);
        }
        int fromIdx = Math.min(visible.size(), page * size);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", visible.subList(fromIdx, Math.min(visible.size(), fromIdx + size)));
        out.put("page", page);
        out.put("size", size);
        out.put("total", visible.size());
        return out;
    }

    // ---------------------------------------------------------------- 내부

    private void startOcr(long id) {
        worker.submit(() -> runOcr(id));
    }

    void runOcr(long id) {
        try {
            Map<String, Object> r = row(id);
            Map<String, Object> form = form(String.valueOf(r.get("form_type")));
            FileStore.Stored f = files.find(((Number) r.get("file_id")).longValue());
            @SuppressWarnings("unchecked")
            OcrEngine.Result res = ocr.recognize(files.read(f), f.mime(), f.name(), new OcrEngine.Form(
                    String.valueOf(form.get("formType")), String.valueOf(form.get("name")),
                    (List<Map<String, Object>>) form.get("fields")));
            jdbc.update("""
                    UPDATE ocr_result SET fields = CAST(:fields AS JSONB), confidence = :conf, status = 'REVIEW'
                    WHERE ocr_id = :id AND status = 'RECOGNIZING'
                    """, new MapSqlParameterSource("fields", write(res.fields())).addValue("conf", res.confidence())
                    .addValue("id", id));
        } catch (RuntimeException e) {
            log.warn("서류 인식 실패 id={}: {}", id, e.getMessage());
            jdbc.update("UPDATE ocr_result SET status = 'FAILED' WHERE ocr_id = :id", new MapSqlParameterSource("id", id));
        }
    }

    private UserScope admin() {
        return currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
    }

    private static boolean inScope(UserScope scope, Long processId, Long factoryId, MasterCache.Snapshot m) {
        if (processId == null) {
            return factoryId == null;
        }
        if (factoryId != null) {
            CentralDto.Process p = m.process(processId);
            if (p == null || p.factoryId() != factoryId) {
                return false;
            }
        }
        return scope.canProcess(processId, m);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForList("SELECT * FROM ocr_result WHERE ocr_id = :id", new MapSqlParameterSource("id", id))
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "등록한 서류가 없습니다."));
    }

    private Map<String, Object> form(String formType) {
        Map<String, Object> r = jdbc.queryForList("SELECT * FROM doc_form WHERE form_type = :f",
                new MapSqlParameterSource("f", formType)).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "양식을 확인하세요: " + formType));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("formType", r.get("form_type"));
        out.put("name", r.get("name"));
        out.put("fields", json(r.get("field_schema")));
        return out;
    }

    static int needCheckCount(List<Map<String, Object>> fields) {
        int need = 0;
        for (Map<String, Object> f : fields) {
            if (Boolean.TRUE.equals(f.get("needCheck"))) {
                need++;
            }
        }
        return need;
    }

    private List<Map<String, Object>> fields(Map<String, Object> r) {
        Object v = json(r.get("fields"));
        if (v instanceof List<?> l) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> out = (List<Map<String, Object>>) l;
            return out;
        }
        return List.of();
    }

    /** 화면이 보낸 값으로 칸을 바꾼다. checked=true 면 확인 필요를 끈다 (테스트용 static) */
    static List<Map<String, Object>> merge(List<Map<String, Object>> fields, List<Map<String, Object>> changes) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            Map<String, Object> copy = new LinkedHashMap<>(f);
            for (Map<String, Object> c : changes) {
                if (String.valueOf(f.get("key")).equals(String.valueOf(c.get("key")))) {
                    if (c.containsKey("value")) {
                        copy.put("value", c.get("value") == null ? "" : String.valueOf(c.get("value")));
                    }
                    if (Boolean.TRUE.equals(c.get("checked"))) {
                        copy.put("needCheck", false);
                        copy.remove("reason");
                    }
                }
            }
            out.add(copy);
        }
        return out;
    }

    /** 날짜·숫자 칸 형식이 틀린 칸 key 목록 (빈 값은 통과) */
    @SuppressWarnings("unchecked")
    static List<String> validate(List<Map<String, Object>> fields, Map<String, Object> form) {
        Map<String, String> types = new LinkedHashMap<>();
        Map<String, List<String>> options = new LinkedHashMap<>();
        for (Map<String, Object> d : (List<Map<String, Object>>) form.get("fields")) {
            types.put(String.valueOf(d.get("key")), String.valueOf(d.get("type")));
            if (d.get("options") instanceof List<?> o) {
                options.put(String.valueOf(d.get("key")), o.stream().map(String::valueOf).toList());
            }
        }
        List<String> bad = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            String key = String.valueOf(f.get("key"));
            String v = f.get("value") == null ? "" : String.valueOf(f.get("value")).strip();
            if (v.isEmpty()) {
                continue;
            }
            String t = types.get(key);
            if ("number".equals(t) && !v.matches("-?\\d+(\\.\\d+)?")) {
                bad.add(key);
            } else if ("date".equals(t) && !v.matches("\\d{4}-\\d{2}-\\d{2}")) {
                bad.add(key);
            } else if ("time".equals(t) && !v.matches("\\d{1,2}:\\d{2}")) {
                bad.add(key);
            } else if ("choice".equals(t) && options.containsKey(key) && !options.get(key).contains(v)) {
                bad.add(key);
            }
        }
        return bad;
    }

    static String fileKind(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase();
        String mm = mime == null ? "" : mime.toLowerCase();
        if (mm.contains("pdf") || n.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (mm.contains("png") || n.endsWith(".png")) {
            return "image/png";
        }
        if (mm.contains("jpeg") || mm.contains("jpg") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return null;
    }

    /** 양식을 비워 보내면 파일 이름으로 짐작 (없으면 F02) */
    static String guessForm(String fileName) {
        String n = fileName == null ? "" : fileName;
        if (n.contains("밀폐")) {
            return "F01";
        }
        if (n.contains("가스")) {
            return "F03";
        }
        if (n.contains("건조로") || n.contains("온도")) {
            return "F04";
        }
        if (n.contains("소화")) {
            return "F05";
        }
        return "F02";
    }

    private Object json(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return mapper.readValue(v.toString(), new TypeReference<Object>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private String write(Object v) {
        try {
            return mapper.writeValueAsString(v);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static LocalDate toDate(Object v) {
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        return LocalDate.parse(String.valueOf(v).substring(0, 10));
    }

    private static Object zoned(Object v) {
        if (v instanceof OffsetDateTime t) {
            return t.atZoneSameInstant(SimTime.ZONE).toOffsetDateTime();
        }
        if (v instanceof java.sql.Timestamp t) {
            return t.toInstant().atZone(SimTime.ZONE).toOffsetDateTime();
        }
        return v;
    }
}
