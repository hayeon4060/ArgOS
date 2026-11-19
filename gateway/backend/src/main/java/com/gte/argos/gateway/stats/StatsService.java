package com.gte.argos.gateway.stats;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.alarm.AlarmService;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralClient.AlarmQuery;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.CentralDto.Alarm;
import com.gte.argos.gateway.central.CentralDto.Event;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.checklist.ChecklistRepository;
import com.gte.argos.gateway.checklist.ChecklistRepository.Checklist;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 통계·이력 관리 (API 명세서 D1).
 * 기간 안의 경보를 중앙에서 모두 받아 조건으로 거른 뒤 연계 서버에서 센다.
 */
@Service
public class StatsService {

    private static final int PAGE = 500;
    private static final int EXPORT_LIMIT = 50_000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final ChecklistRepository checklists;
    private final AlarmService alarms;
    private final ActivityLogger activity;

    public StatsService(CentralClient central, MasterCache masterCache, CurrentUser currentUser,
                        ChecklistRepository checklists, AlarmService alarms, ActivityLogger activity) {
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.checklists = checklists;
        this.alarms = alarms;
        this.activity = activity;
    }

    /** GET /stats/summary — 요약 4칸과 직전 기간 대비 변화 */
    public Map<String, Object> summary(AlarmCriteria c) {
        UserScope scope = scope(c);
        List<Alarm> now = filtered(c, scope);
        List<Alarm> before = filtered(c.previous(), scope);
        Numbers a = numbers(now);
        Numbers b = numbers(before);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alarmCount", metric(a.count, b.count));
        out.put("dangerCount", metric(a.danger, b.danger));
        out.put("avgActionMinutes", metric(a.avgActionMinutes, b.avgActionMinutes));
        out.put("checklistDoneRate", metric(a.doneRate, b.doneRate));
        return out;
    }

    /**
     * GET /stats/trend
     *
     * @param unit HOUR | DAY | WEEK. 비우면 1일 이하 HOUR, 62일 이하 DAY, 그 이상 WEEK
     */
    public Map<String, Object> trend(AlarmCriteria c, String unit) {
        UserScope scope = scope(c);
        String u = unit != null && !unit.isBlank() ? unit : c.days() <= 1 ? "HOUR" : c.days() <= 62 ? "DAY" : "WEEK";
        Map<String, int[]> buckets = new LinkedHashMap<>(); // [warn, alarm, day, night]
        OffsetDateTime t = c.start();
        while (t.isBefore(c.end())) {
            buckets.put(key(t, u), new int[4]);
            t = switch (u) {
                case "HOUR" -> t.plusHours(1);
                case "WEEK" -> t.plusWeeks(1);
                default -> t.plusDays(1);
            };
        }
        for (Alarm a : filtered(c, scope)) {
            int[] b = buckets.computeIfAbsent(key(a.raisedAt(), u), k -> new int[4]);
            b[a.stage() >= 3 ? 1 : 0]++;
            b[AlarmCriteria.isDayShift(a.raisedAt()) ? 2 : 3]++;
        }
        List<Map<String, Object>> points = new ArrayList<>();
        buckets.forEach((k, b) -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("key", k);
            p.put("warn", b[0]);
            p.put("alarm", b[1]);
            p.put("day", b[2]);
            p.put("night", b[3]);
            points.add(p);
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unit", u);
        out.put("points", points);
        return out;
    }

    /** GET /stats/by-process — 공정별 · 센서 종류별 건수 */
    public List<Map<String, Object>> byProcess(AlarmCriteria c) {
        UserScope scope = scope(c);
        MasterCache.Snapshot m = masterCache.get();
        Map<Long, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (Alarm a : filtered(c, scope)) {
            Map<String, Integer> row = counts.computeIfAbsent(a.processId(), k -> {
                Map<String, Integer> z = new LinkedHashMap<>();
                for (String t : List.of("CURRENT", "TEMP", "CO", "IR")) {
                    z.put(t, 0);
                }
                return z;
            });
            Event e = a.mainEvent();
            if (e != null) {
                row.merge(Codes.sensorType(e.type()), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        counts.forEach((pid, row) -> {
            CentralDto.Process p = m.process(pid);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("processId", pid);
            r.put("processName", p == null ? null : p.name());
            r.putAll(row);
            r.put("total", row.values().stream().mapToInt(Integer::intValue).sum());
            out.add(r);
        });
        out.sort(Comparator.comparing((Map<String, Object> r) -> (Integer) r.get("total")).reversed());
        return out;
    }

    /** GET /stats/heatmap — 요일(월=1) × 시간(0~23), 0건 칸은 뺀다 */
    public Map<String, Object> heatmap(AlarmCriteria c) {
        UserScope scope = scope(c);
        Map<String, Integer> cells = new HashMap<>();
        for (Alarm a : filtered(c, scope)) {
            OffsetDateTime t = a.raisedAt().atZoneSameInstant(SimTime.ZONE).toOffsetDateTime();
            cells.merge(t.getDayOfWeek().getValue() + ":" + t.getHour(), 1, Integer::sum);
        }
        int max = 0;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cells.entrySet()) {
            String[] k = e.getKey().split(":");
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("dow", Integer.parseInt(k[0]));
            cell.put("hour", Integer.parseInt(k[1]));
            cell.put("count", e.getValue());
            out.add(cell);
            max = Math.max(max, e.getValue());
        }
        out.sort(Comparator.comparing((Map<String, Object> x) -> (Integer) x.get("dow"))
                .thenComparing(x -> (Integer) x.get("hour")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("max", max);
        result.put("cells", out);
        return result;
    }

    /**
     * GET /alarms 의 이력 표 (D1-5). 조건 파라미터 + 정렬 + 쪽 나눔.
     *
     * @param sort raisedAt,desc (기본) | actionMinutes,desc
     */
    public Map<String, Object> history(AlarmCriteria c, String sort, int page, int size) {
        UserScope scope = scope(c);
        List<Alarm> list = new ArrayList<>(filtered(c, scope));
        if (sort != null && sort.startsWith("actionMinutes")) {
            list.sort(Comparator.comparing((Alarm a) -> actionMinutes(a) == null ? -1L : actionMinutes(a)).reversed());
        }
        int from = Math.min(list.size(), page * size);
        List<Alarm> pageItems = list.subList(from, Math.min(list.size(), from + size));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", alarms.items(pageItems, masterCache.get()));
        out.put("page", page);
        out.put("size", size);
        out.put("total", list.size());
        return out;
    }

    /** GET /stats/alarms/{alarmId} — 알림 상세 패널 */
    public Map<String, Object> alarmDetail(long alarmId) {
        return alarms.historyDetail(alarmId);
    }

    /** GET /stats/export?format=csv — 지금 조건 그대로 알림 이력 파일 */
    public Export export(AlarmCriteria c, String format) {
        UserScope scope = scope(c);
        List<Alarm> list = filtered(c, scope);
        if (list.size() > EXPORT_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPORT_TOO_LARGE", "5만 건을 넘습니다. 기간을 줄여 주세요.");
        }
        MasterCache.Snapshot m = masterCache.get();
        List<Map<String, Object>> rows = alarms.items(list, m);
        // 열 순서: stats-history-api.md 3장 (2026-11-19)
        List<String> header = List.of("알림 번호", "발생 시각", "해제 시각", "유형", "공장", "공정", "설비", "등급",
                "알림 센서", "센서 종류", "발생 값", "기준값", "단위", "상태", "확인자", "조치", "조치 담당자",
                "체크리스트", "조치 시간(분)");
        List<List<Object>> table = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Alarm a = list.get(i);
            Map<String, Object> r = rows.get(i);
            CentralDto.Factory f = m.factory(a.factoryId());
            CentralDto.Process p = m.process(a.processId());
            @SuppressWarnings("unchecked")
            Map<String, Object> ck = (Map<String, Object>) r.get("checklist");
            CentralDto.Equipment eq = r.get("equipmentId") == null ? null
                    : m.equipment(((Number) r.get("equipmentId")).longValue());
            table.add(java.util.Arrays.asList(
                    a.alarmId(),
                    a.raisedAt() == null ? "" : CSV_TIME.format(a.raisedAt().atZoneSameInstant(SimTime.ZONE)),
                    a.closedAt() == null ? "" : CSV_TIME.format(a.closedAt().atZoneSameInstant(SimTime.ZONE)),
                    hazardName(a.hazard()), f == null ? "" : f.name(), p == null ? "" : p.name(),
                    eq == null ? a.unitName() : eq.name(), gradeName(a), r.get("sensorTag"),
                    sensorTypeName((String) r.get("sensorType")), r.get("value"), r.get("threshold"), r.get("unit"),
                    statusName(a.status()), r.get("ackedBy"), a.stage() >= 3 ? "설비 셧다운" : (ck == null ? "" : "체크리스트"),
                    r.get("assignee"), a.stage() >= 3 || ck == null ? "" : ck.get("doneCount") + "/" + ck.get("totalCount"),
                    r.get("actionMinutes")));
        }
        boolean xlsx = "xlsx".equalsIgnoreCase(format);
        String base = "ArgOS_alarm_" + c.from().toString().replace("-", "") + "_" + c.to().toString().replace("-", "");
        activity.log(scope.userId(), "EXPORT", null, c.processId(), "alarm", null,
                "알림 이력 내보내기 " + c.from() + " ~ " + c.to() + " (" + list.size() + "건, " + (xlsx ? "xlsx" : "csv") + ")");
        if (xlsx) {
            return new Export(base + ".xlsx", com.gte.argos.gateway.report.SimpleXlsx.write("알림 이력", header, table), XLSX);
        }
        StringBuilder sb = new StringBuilder("\uFEFF").append(String.join(",", header)).append('\n');
        for (List<Object> row : table) {
            for (int k = 0; k < row.size(); k++) {
                if (k > 0) {
                    sb.append(',');
                }
                sb.append(csv(row.get(k)));
            }
            sb.append('\n');
        }
        return new Export(base + ".csv", sb.toString().getBytes(StandardCharsets.UTF_8), "text/csv; charset=UTF-8");
    }

    /** 한 번에 조회할 수 있는 기간 (from ~ to, 양 끝 포함). 2026-11-19 1년 → 30일 */
    static final int MAX_DAYS = 30;

    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static String hazardName(String hazard) {
        return "GAS".equals(hazard) ? "가스" : "FIRE".equals(hazard) ? "화재" : "";
    }

    /** 등급: 주의 / 경보 / 화재 (화재 경보는 '화재') */
    static String gradeName(Alarm a) {
        if (a.stage() < 3) {
            return "주의";
        }
        return "FIRE".equals(a.hazard()) ? "화재" : "경보";
    }

    static String statusName(String status) {
        return switch (status == null ? "" : status) {
            case "OPEN" -> "확인 대기";
            case "ACKED" -> "확인함";
            case "ACTING" -> "조치 중";
            case "CLOSED" -> "해제";
            default -> "";
        };
    }

    static String sensorTypeName(String type) {
        return switch (type == null ? "" : type) {
            case "CURRENT" -> "전류";
            case "TEMP" -> "온도";
            case "CO" -> "CO";
            case "IR" -> "열화상";
            default -> type == null ? "" : type;
        };
    }

    // ---------------------------------------------------------------

    private UserScope scope(AlarmCriteria c) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        if (c.processId() != null) {
            currentUser.checkProcess(scope, c.processId());
        } else if (c.factoryId() != null) {
            currentUser.checkFactory(scope, c.factoryId());
        }
        //   프론트는 이미 30일로 막음. 자세한 건 docs/api/stats-history-30days.md
        if (c.days() > MAX_DAYS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RANGE_TOO_LARGE", "한 번에 30일까지 조회할 수 있습니다.");
        }
        return scope;
    }

    /** 기간 안의 경보 전부 (쪽마다 500건씩) → 조건·담당 범위로 거르기. 최근 것이 앞 */
    List<Alarm> filtered(AlarmCriteria c, UserScope scope) {
        MasterCache.Snapshot m = masterCache.get();
        List<Alarm> out = new ArrayList<>();
        int page = 0;
        while (true) {
            CentralDto.Page<Alarm> p = central.alarms(new AlarmQuery(c.start(), c.end(), null, null, null,
                    c.processId(), c.factoryId(), null, page, PAGE));
            for (Alarm a : p.items()) {
                if (c.matches(a, scope, m)) {
                    out.add(a);
                }
            }
            page++;
            if ((long) page * PAGE >= p.total() || p.items().isEmpty() || page > 200) {
                break;
            }
        }
        return out;
    }

    private Numbers numbers(List<Alarm> list) {
        Numbers n = new Numbers();
        n.count = list.size();
        long sum = 0;
        int closed = 0;
        List<Long> ids = new ArrayList<>();
        for (Alarm a : list) {
            if (a.stage() >= 3) {
                n.danger++;
            }
            Long minutes = actionMinutes(a);
            if (minutes != null) {
                sum += minutes;
                closed++;
            }
            ids.add(a.alarmId());
        }
        n.avgActionMinutes = closed == 0 ? null : Math.round(sum / (double) closed * 10) / 10.0;
        int done = 0;
        int total = 0;
        for (Checklist ck : checklists.list(null, null, ids)) {
            done += ck.doneCount();
            total += ck.items().size();
        }
        n.doneRate = total == 0 ? null : Math.round(done * 1000.0 / total) / 10.0;
        return n;
    }

    private static Long actionMinutes(Alarm a) {
        if (a.closedAt() == null || a.raisedAt() == null) {
            return null;
        }
        return Math.max(1, Duration.between(a.raisedAt(), a.closedAt()).toMinutes());
    }

    private static Map<String, Object> metric(Number now, Number before) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", now);
        if (now == null || before == null) {
            m.put("diff", null);
        } else {
            double d = now.doubleValue() - before.doubleValue();
            m.put("diff", d == Math.rint(d) ? (Object) (long) d : (Object) (Math.round(d * 10) / 10.0));
        }
        return m;
    }

    private static String key(OffsetDateTime t, String unit) {
        OffsetDateTime k = t.atZoneSameInstant(SimTime.ZONE).toOffsetDateTime();
        return switch (unit) {
            case "HOUR" -> k.truncatedTo(ChronoUnit.HOURS).toLocalDateTime().toString();
            case "WEEK" -> k.getYear() + "-W" + String.format("%02d", k.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            default -> k.toLocalDate().toString();
        };
    }

    private static String csv(Object v) {
        if (v == null) {
            return "";
        }
        String s = String.valueOf(v);
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static final class Numbers {
        int count;
        int danger;
        Double avgActionMinutes;
        Double doneRate;
    }

    public record Export(String fileName, byte[] body, String contentType) {
    }
}
