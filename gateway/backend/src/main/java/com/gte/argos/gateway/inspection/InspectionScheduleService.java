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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 점검 관리 개편 A안 (docs/api/inspection-redesign-api.md): 점검 건(inspection_schedule) 목록 · 추가 · 전자 작성 · 스캔본 연결.
 *
 * <pre>
 * 상태   DONE 완료 / PLANNED 예정 / DELAYED 누락 (예정 시각이 지났는데 안 함)
 * 구분   PAPER 수기 (스캔본 검수 완료) / DIGITAL 전자 (화면 양식에 바로 작성). 완료 건만
 * 결과   OK 양호 / NG 불량 (양식 값에 '불량'이 하나라도 있으면). 완료 건만
 * 설비번호 equipmentNo: EF-01 … 공정 안 설비(카메라 제외)를 설비 id 순으로 센 번호 (양식의 '설비번호' 칸과 맞춤)
 * 권한   보기: 본사 관리자 · 공장 담당자 / 추가 · 작성: 공장 담당자
 * </pre>
 */
@Service
public class InspectionScheduleService {

    static final List<String> REPEATS = List.of("ONCE", "DAILY", "WEEKLY", "MONTHLY");
    static final int MAX_COUNT = 31;
    static final int MAX_RANGE_DAYS = 62;
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final MasterCache masterCache;
    private final ActivityLogger activity;
    private final ObjectMapper mapper;
    private final SimTime time;

    public InspectionScheduleService(NamedParameterJdbcTemplate jdbc, CurrentUser currentUser, MasterCache masterCache,
                                     ActivityLogger activity, ObjectMapper mapper, SimTime time) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.masterCache = masterCache;
        this.activity = activity;
        this.mapper = mapper;
        this.time = time;
    }

    // ---------------------------------------------------------------- 3-1 목록

    /**
     * GET /inspections/schedule — date(하루) · month(YYYY-MM) · from&to 중 하나. 담당 범위 안 공정만.
     * 날짜 · 시각순 (화면이 최신순으로 뒤집어 씀).
     */
    public List<Map<String, Object>> list(LocalDate date, String month, LocalDate from, LocalDate to, Long factoryId,
                                          String formType) {
        UserScope scope = viewer();
        if (factoryId != null) {
            currentUser.checkFactory(scope, factoryId);
        }
        LocalDate[] range = range(date, month, from, to);
        MapSqlParameterSource p = new MapSqlParameterSource("from", range[0]).addValue("to", range[1]);
        String where = "s.planned_at BETWEEN :from AND :to";
        if (formType != null && !formType.isBlank()) {
            where += " AND s.form_type = :form";
            p.addValue("form", formType);
        }
        return rows(where, p, scope, factoryId);
    }

    /** 범위 [from, to] (양 끝 포함) */
    static LocalDate[] range(LocalDate date, String month, LocalDate from, LocalDate to) {
        if (date != null) {
            return new LocalDate[] {date, date};
        }
        if (month != null && !month.isBlank()) {
            YearMonth ym;
            try {
                ym = YearMonth.parse(month);
            } catch (RuntimeException e) {
                throw bad("month 는 YYYY-MM 입니다.");
            }
            return new LocalDate[] {ym.atDay(1), ym.atEndOfMonth()};
        }
        if (from != null && to != null) {
            if (to.isBefore(from)) {
                throw bad("기간을 확인하세요.");
            }
            if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RANGE_TOO_LARGE",
                        "한 번에 " + MAX_RANGE_DAYS + "일까지 조회할 수 있습니다.");
            }
            return new LocalDate[] {from, to};
        }
        throw bad("date · month · from&to 중 하나를 넣어 주세요.");
    }

    private List<Map<String, Object>> rows(String where, MapSqlParameterSource p, UserScope scope, Long factoryId) {
        OffsetDateTime now = time.now();
        MasterCache.Snapshot m = masterCache.get();
        List<Map<String, Object>> raw = jdbc.queryForList("""
                SELECT s.*, u.name AS assignee, d.name AS form_name, du.name AS done_by_name,
                       o.ocr_id AS scan_id, f.orig_name AS scan_name
                FROM inspection_schedule s
                LEFT JOIN app_user u ON u.user_id = s.assignee_id
                LEFT JOIN app_user du ON du.user_id = s.done_by
                LEFT JOIN doc_form d ON d.form_type = s.form_type
                LEFT JOIN LATERAL (
                    SELECT ocr_id, file_id FROM ocr_result o
                    WHERE o.schedule_id = s.schedule_id AND o.status = 'DONE'
                    ORDER BY o.reviewed_at DESC NULLS LAST, o.ocr_id DESC LIMIT 1) o ON TRUE
                LEFT JOIN file_asset f ON f.file_id = o.file_id
                WHERE """ + " " + where + """
                 ORDER BY s.planned_at, s.planned_time NULLS LAST, s.schedule_id
                """, p);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Long pid = lng(r.get("process_id"));
            if (!inScope(scope, pid, factoryId, m)) {
                continue;
            }
            out.add(view(r, m, now));
        }
        return out;
    }

    private Map<String, Object> view(Map<String, Object> r, MasterCache.Snapshot m, OffsetDateTime now) {
        Long pid = lng(r.get("process_id"));
        Long eqId = lng(r.get("equipment_id"));
        CentralDto.Equipment eq = eqId == null ? null : m.equipment(eqId);
        CentralDto.Process p = pid == null ? null : m.process(pid);
        CentralDto.Factory f = p == null ? null : m.factory(p.factoryId());
        String status = InspectionService.scheduleStatus(r, now);
        boolean done = "DONE".equals(status);
        Long scanId = lng(r.get("scan_id"));
        String source = (String) r.get("source");
        if (done && source == null) {
            source = scanId != null ? "PAPER" : r.get("form_values") != null ? "DIGITAL" : null;
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("scheduleId", r.get("schedule_id"));
        v.put("date", String.valueOf(r.get("planned_at")));
        v.put("time", r.get("planned_time") == null ? null : r.get("planned_time").toString().substring(0, 5));
        v.put("title", r.get("title"));
        v.put("formType", r.get("form_type"));
        v.put("formName", r.get("form_name"));
        v.put("processId", pid);
        v.put("processName", (f == null ? "" : f.name() + " ") + (p == null ? "" : p.name()));
        v.put("equipmentId", eqId);
        v.put("equipmentName", eq == null ? null : eq.name());
        v.put("equipmentNo", eq == null ? null : equipmentNo(eq, m));
        v.put("assigneeId", r.get("assignee_id"));
        v.put("assignee", r.get("assignee"));
        v.put("status", status);
        v.put("source", done ? source : null);
        v.put("result", done ? r.get("result") : null);
        v.put("inspectionId", done && "PAPER".equals(source) ? scanId : null);
        v.put("scanName", done && "PAPER".equals(source) ? r.get("scan_name") : null);
        v.put("doneAt", zoned(r.get("done_at")));
        v.put("doneBy", r.get("done_by_name"));
        return v;
    }

    /** 공정 안 설비(카메라 제외)를 설비 id 순으로 센 번호 EF-01 … */
    static String equipmentNo(CentralDto.Equipment eq, MasterCache.Snapshot m) {
        List<CentralDto.Equipment> list = numbered(eq.processId(), m);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == eq.id()) {
                return String.format("EF-%02d", i + 1);
            }
        }
        return null;
    }

    private static List<CentralDto.Equipment> numbered(long processId, MasterCache.Snapshot m) {
        return m.equipmentOf(processId).stream()
                .filter(e -> e.active() && !"CAMERA".equals(e.type()))
                .sorted(Comparator.comparingLong(CentralDto.Equipment::id))
                .toList();
    }

    /** 설비번호(EF-07, 07, 7)의 숫자 → 그 공정의 설비. 못 찾으면 null */
    static CentralDto.Equipment byNo(String no, long processId, MasterCache.Snapshot m) {
        Integer n = number(no);
        if (n == null) {
            return null;
        }
        List<CentralDto.Equipment> list = numbered(processId, m);
        return n >= 1 && n <= list.size() ? list.get(n - 1) : null;
    }

    static Integer number(String no) {
        if (no == null) {
            return null;
        }
        Matcher mt = DIGITS.matcher(no);
        Integer last = null;
        while (mt.find()) {
            last = Integer.parseInt(mt.group(1));
        }
        return last;
    }

    // ---------------------------------------------------------------- 3-2 고르는 칸

    /** GET /inspections/schedule/options?factoryId= — 점검 설비(F02, 카메라 제외)와 점검자(그 공장 담당 사용자) */
    public Map<String, Object> options(Long factoryId) {
        UserScope scope = viewer();
        MasterCache.Snapshot m = masterCache.get();
        Long fid = factoryId;
        if (fid == null && scope.factoryIds() != null && scope.factoryIds().size() == 1) {
            fid = scope.factoryIds().iterator().next();
        }
        if (fid == null) {
            throw bad("factoryId 를 넣어 주세요.");
        }
        currentUser.checkFactory(scope, fid);
        List<Map<String, Object>> equipments = new ArrayList<>();
        for (CentralDto.Process p : m.processesOf(fid)) {
            List<CentralDto.Equipment> list = numbered(p.id(), m);
            for (int i = 0; i < list.size(); i++) {
                CentralDto.Equipment e = list.get(i);
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("id", e.id());
                v.put("name", e.name());
                v.put("code", String.format("EF-%02d", i + 1));
                v.put("formType", "F02");
                v.put("processId", p.id());
                v.put("processName", p.name());
                equipments.add(v);
            }
        }
        Long plantId = m.plantOfFactory(fid);
        List<Map<String, Object>> inspectors = jdbc.queryForList("""
                SELECT DISTINCT u.user_id AS id, u.name FROM app_user u
                JOIN user_assignment a ON a.user_id = u.user_id
                JOIN role r ON r.role_id = u.role_id
                WHERE u.is_active AND u.deleted_at IS NULL AND r.code IN ('PLANT_ADMIN', 'WORKER')
                  AND (a.factory_id = :f OR (a.factory_id IS NULL AND a.plant_id = :p))
                ORDER BY u.name
                """, new MapSqlParameterSource("f", fid).addValue("p", plantId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipments", equipments);
        out.put("inspectors", inspectors);
        return out;
    }

    // ---------------------------------------------------------------- 3-3 추가

    /** POST /inspections/schedule — 반복이면 count 건. 응답: 만든 건 목록 (3-1 줄 모양) */
    @Transactional
    public List<Map<String, Object>> create(Map<String, Object> body) {
        UserScope scope = writer();
        LocalDate date = date(body.get("date"), "date");
        LocalTime t = time(body.get("time"));
        String formType = str(body.get("formType")) == null ? "F02" : str(body.get("formType"));
        String formName = formName(formType);
        long equipmentId = lngReq(body.get("equipmentId"), "equipmentId");
        MasterCache.Snapshot m = masterCache.get();
        CentralDto.Equipment eq = m.equipment(equipmentId);
        if (eq == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        currentUser.checkProcess(scope, eq.processId());
        Long assignee = lng(body.get("assigneeId"));
        String repeat = str(body.get("repeat")) == null ? "ONCE" : str(body.get("repeat"));
        if (!REPEATS.contains(repeat)) {
            throw bad("repeat 는 ONCE · DAILY · WEEKLY · MONTHLY 중 하나입니다.");
        }
        int count = "ONCE".equals(repeat) ? 1 : body.get("count") == null ? 1 : ((Number) body.get("count")).intValue();
        if (count < 1 || count > MAX_COUNT) {
            throw bad("count 는 1 ~ " + MAX_COUNT + " 입니다.");
        }
        List<Long> ids = new ArrayList<>();
        for (LocalDate d : dates(date, repeat, count)) {
            ids.add(jdbc.queryForObject("""
                    INSERT INTO inspection_schedule (equipment_id, process_id, assignee_id, planned_at, planned_time,
                                                     form_type, status, title)
                    VALUES (:eq, :p, :a, :d, :t, :f, 'PLANNED', :title) RETURNING schedule_id
                    """, new MapSqlParameterSource("eq", equipmentId).addValue("p", eq.processId()).addValue("a", assignee)
                    .addValue("d", d).addValue("t", t).addValue("f", formType).addValue("title", formName), Long.class));
        }
        activity.log(scope.userId(), "INSPECTION", scope.siteId(), eq.processId(), "inspection_schedule", ids.get(0),
                "점검 일정 추가: " + eq.name() + " " + formName + " " + ids.size() + "건");
        return byIds(ids, scope);
    }

    /** 반복 날짜들 */
    static List<LocalDate> dates(LocalDate first, String repeat, int count) {
        List<LocalDate> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(switch (repeat) {
                case "DAILY" -> first.plusDays(i);
                case "WEEKLY" -> first.plusWeeks(i);
                case "MONTHLY" -> first.plusMonths(i);
                default -> first;
            });
        }
        return out;
    }

    // ---------------------------------------------------------------- 3-4 양식 값

    /** GET /inspections/schedule/{id}/form — 수기는 검수 완료 값, 전자는 저장 값, 예정 · 누락은 null */
    public Map<String, Object> form(long scheduleId) {
        UserScope scope = viewer();
        Map<String, Object> r = schedule(scheduleId, scope);
        Map<String, Object> scan = latestScan(scheduleId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scheduleId", scheduleId);
        out.put("formType", r.get("form_type"));
        if (scan != null) {
            out.put("source", "PAPER");
            out.put("values", values(json(scan.get("fields"))));
        } else if (r.get("form_values") != null) {
            out.put("source", "DIGITAL");
            out.put("values", json(r.get("form_values")));
        } else {
            out.put("source", null);
            out.put("values", null);
        }
        out.put("result", r.get("result"));
        return out;
    }

    // ---------------------------------------------------------------- 3-5 전자 작성

    /** PUT /inspections/schedule/{id}/form — 예정 · 누락 건을 양식으로 작성 → 완료 · 전자. 이미 완료면 409 */
    @Transactional
    public Map<String, Object> saveForm(long scheduleId, Map<String, Object> body) {
        UserScope scope = writer();
        Map<String, Object> r = schedule(scheduleId, scope);
        if ("DONE".equals(r.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DONE", "이미 완료된 점검 건입니다.");
        }
        Map<String, Object> values = valuesOf(body);
        String result = result(body.get("result"), values);
        jdbc.update("""
                UPDATE inspection_schedule SET status = 'DONE', source = 'DIGITAL', result = :result,
                       form_values = CAST(:values AS JSONB), done_at = :now, done_by = :by
                WHERE schedule_id = :id
                """, new MapSqlParameterSource("result", result).addValue("values", write(values))
                .addValue("now", time.now()).addValue("by", scope.userId()).addValue("id", scheduleId));
        activity.log(scope.userId(), "INSPECTION", scope.siteId(), lng(r.get("process_id")), "inspection_schedule",
                scheduleId, "점검일지 전자 작성 (" + formName(String.valueOf(r.get("form_type"))) + ", " + result + ")");
        return byIds(List.of(scheduleId), scope).get(0);
    }

    /** POST /inspections/records — 스케줄 없이 '점검일지 작성': 완료 · 전자 건을 바로 만든다 */
    @Transactional
    public Map<String, Object> createRecord(Map<String, Object> body) {
        UserScope scope = writer();
        String formType = str(body.get("formType")) == null ? "F02" : str(body.get("formType"));
        String formName = formName(formType);
        long equipmentId = lngReq(body.get("equipmentId"), "equipmentId");
        CentralDto.Equipment eq = masterCache.get().equipment(equipmentId);
        if (eq == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        currentUser.checkProcess(scope, eq.processId());
        LocalDate date = body.get("date") == null ? time.now().atZoneSameInstant(SimTime.ZONE).toLocalDate()
                : date(body.get("date"), "date");
        LocalTime t = time(body.get("time"));
        Map<String, Object> values = valuesOf(body);
        String result = result(body.get("result"), values);
        Long id = jdbc.queryForObject("""
                INSERT INTO inspection_schedule (equipment_id, process_id, assignee_id, planned_at, planned_time, form_type,
                                                 status, title, done_at, done_by, source, result, form_values)
                VALUES (:eq, :p, :by, :d, :t, :f, 'DONE', :title, :now, :by, 'DIGITAL', :result, CAST(:values AS JSONB))
                RETURNING schedule_id
                """, new MapSqlParameterSource("eq", equipmentId).addValue("p", eq.processId()).addValue("by", scope.userId())
                .addValue("d", date).addValue("t", t).addValue("f", formType).addValue("title", formName)
                .addValue("now", time.now()).addValue("result", result).addValue("values", write(values)), Long.class);
        activity.log(scope.userId(), "INSPECTION", scope.siteId(), eq.processId(), "inspection_schedule", id,
                "점검일지 작성 (" + eq.name() + ", " + formName + ", " + result + ")");
        return byIds(List.of(id), scope).get(0);
    }

    // ---------------------------------------------------------------- 3-7 스캔본 연결

    /**
     * 검수 완료(DONE) 저장 때 스캔본을 점검 건에 잇는다 (PUT /inspections/{id} 의 scheduleId).
     *
     * @param scheduleId 숫자면 그 건을 완료 · 수기로, null 이면 인식 값으로 새 완료 · 수기 건을 만든다
     * @return 이은 점검 건 id
     */
    @Transactional
    public long linkScan(long ocrId, Map<String, Object> ocrRow, List<Map<String, Object>> fields, Long scheduleId,
                         UserScope scope) {
        Map<String, Object> values = values(fields);
        String result = hasBad(values) ? "NG" : "OK";
        OffsetDateTime now = time.now();
        if (scheduleId != null) {
            Map<String, Object> r = schedule(scheduleId, scope);
            if ("DONE".equals(r.get("status"))) {
                Long linked = jdbc.queryForList("SELECT schedule_id FROM ocr_result WHERE ocr_id = :id",
                        new MapSqlParameterSource("id", ocrId), Long.class).stream().findFirst().orElse(null);
                if (!scheduleId.equals(linked)) {
                    throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DONE", "이미 완료된 점검 건입니다.");
                }
            }
            jdbc.update("""
                    UPDATE inspection_schedule SET status = 'DONE', source = 'PAPER', result = :result,
                           done_at = COALESCE(done_at, :now), done_by = COALESCE(done_by, :by)
                    WHERE schedule_id = :id
                    """, new MapSqlParameterSource("result", result).addValue("now", now).addValue("by", scope.userId())
                    .addValue("id", scheduleId));
            jdbc.update("UPDATE ocr_result SET schedule_id = :s WHERE ocr_id = :id",
                    new MapSqlParameterSource("s", scheduleId).addValue("id", ocrId));
            return scheduleId;
        }
        // 연결 안 함: 인식 값으로 새 점검 건
        MasterCache.Snapshot m = masterCache.get();
        Long pid = lng(ocrRow.get("process_id"));
        CentralDto.Equipment eq = pid == null ? null : byNo(str(values.get("equipmentNo")), pid, m);
        if (eq != null) {
            pid = eq.processId();
        }
        LocalDate day = inspectedDate(values);
        if (day == null) {
            day = now.atZoneSameInstant(SimTime.ZONE).toLocalDate();
        }
        String formType = String.valueOf(ocrRow.get("form_type"));
        Long assignee = userByName(str(values.get("inspector")));
        Long id = jdbc.queryForObject("""
                INSERT INTO inspection_schedule (equipment_id, process_id, assignee_id, planned_at, planned_time, form_type,
                                                 status, title, done_at, done_by, source, result)
                VALUES (:eq, :p, :a, :d, :t, :f, 'DONE', :title, :now, :by, 'PAPER', :result)
                RETURNING schedule_id
                """, new MapSqlParameterSource("eq", eq == null ? null : eq.id()).addValue("p", pid).addValue("a", assignee)
                .addValue("d", day).addValue("t", inspectedTime(values)).addValue("f", formType)
                .addValue("title", formName(formType)).addValue("now", now).addValue("by", scope.userId())
                .addValue("result", result), Long.class);
        jdbc.update("UPDATE ocr_result SET schedule_id = :s WHERE ocr_id = :id",
                new MapSqlParameterSource("s", id).addValue("id", ocrId));
        return id;
    }

    /**
     * 스캔본에 맞는 점검 건 추천 (GET /inspections/{id}/ocr 의 scheduleId). 이미 이어져 있으면 그 건.
     * 후보: 같은 양식, 오늘 포함 최근 7일의 예정 · 누락 건. 점수: 설비번호 같음 2 + 날짜 같음 1, 같으면 늦은 날짜. 0점이면 없음.
     */
    public Long recommend(Map<String, Object> ocrRow, List<Map<String, Object>> fields, UserScope scope) {
        Long linked = lng(ocrRow.get("schedule_id"));
        if (linked != null) {
            return linked;
        }
        Map<String, Object> values = values(fields);
        Integer no = number(str(values.get("equipmentNo")));
        LocalDate day = inspectedDate(values);
        LocalDate today = time.now().atZoneSameInstant(SimTime.ZONE).toLocalDate();
        OffsetDateTime now = time.now();
        MasterCache.Snapshot m = masterCache.get();
        Long best = null;
        int bestScore = 0;
        LocalDate bestDay = null;
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT * FROM inspection_schedule WHERE form_type = :f AND status <> 'DONE'
                  AND planned_at BETWEEN :from AND :to
                """, new MapSqlParameterSource("f", ocrRow.get("form_type")).addValue("from", today.minusDays(6))
                .addValue("to", today))) {
            if (!inScope(scope, lng(r.get("process_id")), null, m) || "DONE".equals(InspectionService.scheduleStatus(r, now))) {
                continue;
            }
            Long eqId = lng(r.get("equipment_id"));
            CentralDto.Equipment eq = eqId == null ? null : m.equipment(eqId);
            Integer eqNo = eq == null ? null : number(equipmentNo(eq, m));
            LocalDate d = LocalDate.parse(String.valueOf(r.get("planned_at")));
            int score = (no != null && no.equals(eqNo) ? 2 : 0) + (d.equals(day) ? 1 : 0);
            if (score > bestScore || (score == bestScore && score > 0 && bestDay != null && d.isAfter(bestDay))) {
                best = lng(r.get("schedule_id"));
                bestScore = score;
                bestDay = d;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- 내부

    private List<Map<String, Object>> byIds(List<Long> ids, UserScope scope) {
        return rows("s.schedule_id IN (:ids)", new MapSqlParameterSource("ids", ids), scope, null);
    }

    private Map<String, Object> schedule(long id, UserScope scope) {
        Map<String, Object> r = jdbc.queryForList("SELECT * FROM inspection_schedule WHERE schedule_id = :id",
                new MapSqlParameterSource("id", id)).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "점검 건이 없습니다."));
        Long pid = lng(r.get("process_id"));
        if (pid != null) {
            currentUser.checkProcess(scope, pid);
        }
        return r;
    }

    private Map<String, Object> latestScan(long scheduleId) {
        return jdbc.queryForList("""
                SELECT * FROM ocr_result WHERE schedule_id = :id AND status = 'DONE'
                ORDER BY reviewed_at DESC NULLS LAST, ocr_id DESC LIMIT 1
                """, new MapSqlParameterSource("id", scheduleId)).stream().findFirst().orElse(null);
    }

    private String formName(String formType) {
        return jdbc.queryForList("SELECT name FROM doc_form WHERE form_type = :f", new MapSqlParameterSource("f", formType),
                String.class).stream().findFirst().orElseThrow(() -> bad("양식을 확인하세요: " + formType));
    }

    private Long userByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        List<Long> ids = jdbc.queryForList("SELECT user_id FROM app_user WHERE name = :n AND deleted_at IS NULL",
                new MapSqlParameterSource("n", name.trim()), Long.class);
        return ids.size() == 1 ? ids.get(0) : null;
    }

    /** OCR 칸 목록 [{key, value, …}] → {key: value} */
    @SuppressWarnings("unchecked")
    static Map<String, Object> values(Object fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (fields instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> f && f.get("key") != null) {
                    out.put(String.valueOf(f.get("key")), ((Map<String, Object>) f).get("value"));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> valuesOf(Map<String, Object> body) {
        Object v = body.get("values");
        if (!(v instanceof Map<?, ?>)) {
            throw bad("values 를 넣어 주세요.");
        }
        return new LinkedHashMap<>((Map<String, Object>) v);
    }

    /** 화면이 보낸 result(OK · NG), 없으면 값에 '불량'이 있으면 NG */
    static String result(Object given, Map<String, Object> values) {
        String r = given == null ? null : String.valueOf(given);
        if (r != null && !r.isBlank()) {
            if (!"OK".equals(r) && !"NG".equals(r)) {
                throw bad("result 는 OK · NG 입니다.");
            }
            return r;
        }
        return hasBad(values) ? "NG" : "OK";
    }

    static boolean hasBad(Map<String, Object> values) {
        return values.values().stream().anyMatch(v -> v != null && "불량".equals(String.valueOf(v).trim()));
    }

    /** 점검 날짜 칸: inspectedAt → checkDate → applyDate → workDate */
    static LocalDate inspectedDate(Map<String, Object> values) {
        for (String k : List.of("inspectedAt", "checkDate", "applyDate", "workDate")) {
            String s = str(values.get(k));
            if (s != null && s.length() >= 10) {
                try {
                    return LocalDate.parse(s.substring(0, 10).replace('.', '-').replace('/', '-'));
                } catch (RuntimeException ignored) {
                    // 다음 칸
                }
            }
        }
        return null;
    }

    static LocalTime inspectedTime(Map<String, Object> values) {
        String s = str(values.get("inspectedAt"));
        if (s != null && s.length() >= 16) {
            try {
                return LocalTime.parse(s.substring(11, 16));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private UserScope viewer() {
        return currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
    }

    private UserScope writer() {
        return currentUser.require(UserScope.PLANT_ADMIN);
    }

    private static boolean inScope(UserScope scope, Long processId, Long factoryId, MasterCache.Snapshot m) {
        if (processId == null) {
            return factoryId == null && scope.isHq();
        }
        if (factoryId != null) {
            CentralDto.Process p = m.process(processId);
            if (p == null || p.factoryId() != factoryId) {
                return false;
            }
        }
        return scope.canProcess(processId, m);
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
            throw bad("값을 저장하지 못했습니다.");
        }
    }

    private static LocalDate date(Object v, String name) {
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (RuntimeException e) {
            throw bad(name + " 는 YYYY-MM-DD 입니다.");
        }
    }

    private static LocalTime time(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(String.valueOf(v));
        } catch (RuntimeException e) {
            throw bad("time 은 HH:mm 입니다.");
        }
    }

    private static Long lng(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private static long lngReq(Object v, String name) {
        if (!(v instanceof Number n)) {
            throw bad(name + " 를 넣어 주세요.");
        }
        return n.longValue();
    }

    private static String str(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
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

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }
}
