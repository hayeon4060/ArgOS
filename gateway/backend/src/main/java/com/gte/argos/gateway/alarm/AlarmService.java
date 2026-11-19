package com.gte.argos.gateway.alarm;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralClient.AlarmQuery;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.CentralDto.Alarm;
import com.gte.argos.gateway.central.CentralDto.Equipment;
import com.gte.argos.gateway.central.CentralDto.Event;
import com.gte.argos.gateway.central.CentralDto.SensorStatus;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.checklist.ChecklistRepository;
import com.gte.argos.gateway.checklist.ChecklistRepository.Action;
import com.gte.argos.gateway.checklist.ChecklistRepository.Checklist;
import com.gte.argos.gateway.checklist.ChecklistRepository.Template;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.thermal.ThermalService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 화면 (API 명세서 B1-2·3·4, C0, C1-1, C4-1, C4-4, D1-5, I1-1).
 * 경보 원본·상태는 중앙 DB, 조치 이력·체크리스트는 연계 DB.
 */
@Service
public class AlarmService {

    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final SimTime time;
    private final ChecklistRepository checklists;
    private final ThermalService thermal;

    public AlarmService(CentralClient central, MasterCache masterCache, CurrentUser currentUser, SimTime time,
                        ChecklistRepository checklists, ThermalService thermal) {
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.time = time;
        this.checklists = checklists;
        this.thermal = thermal;
    }

    /**
     * GET /alarms. 범위(siteId·factoryId·processId)는 하나만. 비우면 내 담당 범위 전체.
     *
     * @param date     today | 2026-11-12 (from·to 를 주면 무시)
     * @param minLevel WARN | ALARM
     */
    public Map<String, Object> list(Long siteId, Long factoryId, Long processId, String date,
                                    OffsetDateTime from, OffsetDateTime to, String status, String minLevel,
                                    int page, int size) {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        checkScope(scope, siteId, factoryId, processId);
        OffsetDateTime[] range = from != null && to != null ? new OffsetDateTime[] {from, to} : time.dayRange(date);
        Integer minStage = Codes.stage(minLevel);

        List<Alarm> alarms;
        long total;
        if (siteId == null && factoryId == null && processId == null && !scope.isHq()) {
            // 범위를 안 준 공장·공정 담당자: 넓게 받아서 담당 범위로 거른다
            CentralDto.Page<Alarm> all = central.alarms(new AlarmQuery(range[0], range[1], status, minStage,
                    null, null, null, null, 0, 500));
            List<Alarm> mine = all.items().stream().filter(a -> scope.canProcess(a.processId(), m)).toList();
            total = mine.size();
            int fromIdx = Math.min(mine.size(), page * size);
            alarms = mine.subList(fromIdx, Math.min(mine.size(), fromIdx + size));
        } else {
            CentralDto.Page<Alarm> p = central.alarms(new AlarmQuery(range[0], range[1], status, minStage,
                    null, processId, factoryId, siteId, page, size));
            alarms = p.items();
            total = p.total();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items(alarms, m));
        out.put("page", page);
        out.put("size", size);
        out.put("total", total);
        return out;
    }

    /**
     * GET /alarms/hourly?date= : 그날(기본 어제) 주간 07~19시, 야간 19시 ~ 다음 날 07시의 시간별 주의·경보·조치 건수.
     * 집계 범위 = date 07:00 ~ date+1 07:00 (시연 시계 날짜 기준). 조치는 확인 시각이 범위 안인 것만 센다.
     */
    public Map<String, Object> hourly(Long siteId, Long factoryId, String date) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        checkScope(scope, siteId, factoryId, null);
        LocalDate day = hourlyDate(date, time.now().atZoneSameInstant(SimTime.ZONE).toLocalDate());
        OffsetDateTime[] range = hourlyRange(day);
        List<Alarm> alarms = central.alarms(new AlarmQuery(range[0], range[1], null, null, null, null,
                factoryId, siteId, 0, 500)).items();

        Map<Integer, int[]> byHour = new HashMap<>(); // [warn, alarm, action]
        for (Alarm a : alarms) {
            int h = a.raisedAt().atZoneSameInstant(SimTime.ZONE).getHour();
            int[] c = byHour.computeIfAbsent(h, k -> new int[3]);
            if (a.stage() >= 3) {
                c[1]++;
            } else {
                c[0]++;
            }
            if (a.ackedAt() != null && !a.ackedAt().isBefore(range[0]) && a.ackedAt().isBefore(range[1])) {
                int ah = a.ackedAt().atZoneSameInstant(SimTime.ZONE).getHour();
                byHour.computeIfAbsent(ah, k -> new int[3])[2]++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", day.toString());
        out.put("from", range[0]);
        out.put("to", range[1]);
        out.put("day", shift(byHour, 7, 19));
        out.put("night", shift(byHour, 19, 31));
        return out;
    }

    /** date 를 비우면 어제 (대시보드 '전일 시간대별 알림') */
    static LocalDate hourlyDate(String date, LocalDate today) {
        if (date == null || date.isBlank() || "yesterday".equalsIgnoreCase(date)) {
            return today.minusDays(1);
        }
        if ("today".equalsIgnoreCase(date)) {
            return today;
        }
        return LocalDate.parse(date);
    }

    /** 주간 07시 ~ 다음 날 07시 (야간이 날짜를 넘어감) */
    static OffsetDateTime[] hourlyRange(LocalDate day) {
        OffsetDateTime start = day.atTime(7, 0).atZone(SimTime.ZONE).toOffsetDateTime();
        return new OffsetDateTime[] {start, start.plusDays(1)};
    }

    /** GET /alarms/{alarmId}: 발생 → 확인 → 전송 → 조치 → 종료 */
    public Map<String, Object> timeline(long alarmId) {
        Alarm a = visibleAlarm(alarmId);
        MasterCache.Snapshot m = masterCache.get();
        Map<Long, String> names = checklists.userNames(java.util.Arrays.asList(a.ackedBy(), a.closedBy()));
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(step("RAISED", a.raisedAt(), null, null, null));
        for (Action act : checklists.actions(alarmId)) {
            Map<String, Object> s = step(act.action(), act.doneAt(), act.userName(), act.target(), act.detail());
            s.put("text", ChecklistRepository.actionText(act));
            steps.add(s);
        }
        boolean hasAck = steps.stream().anyMatch(s -> "ACK".equals(s.get("step")));
        if (!hasAck && a.ackedAt() != null) {
            // 셧다운처럼 확인 단추 없이 조치에 들어간 경우. 단계 이름은 조치 이력과 같게 (화면은 ACK·CLOSE 를 봄)
            steps.add(1, step("ACK", a.ackedAt(), names.get(a.ackedBy()), null, null));
        }
        boolean hasClose = steps.stream().anyMatch(s -> "CLOSE".equals(s.get("step")) || "CONFIRM".equals(s.get("step")));
        if (!hasClose && a.closedAt() != null) {
            steps.add(step("CLOSE", a.closedAt(), names.get(a.closedBy()), null, null));
        }
        Checklist c = checklists.latestForAlarm(alarmId);
        Event e = a.mainEvent();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", a.alarmId());
        out.put("level", Codes.level(a.stage()));
        out.put("status", a.status());
        out.put("title", AlarmViews.title(a));
        out.put("location", AlarmViews.location(a, m));
        out.put("sensorTag", e == null ? null : e.tag());
        out.put("value", e == null ? null : e.peak());
        out.put("unit", e == null ? null : e.unit());
        out.put("threshold", e == null ? null : e.threshold());
        out.put("timeline", steps);
        out.put("checklist", c == null ? null : Map.of("id", c.id(), "status", c.status(),
                "doneCount", c.doneCount(), "totalCount", c.items().size()));
        return out;
    }

    /** GET /alarms/{alarmId}/detail: 원인, 그때 값, 추이, 기본 체크리스트 원인 후보 */
    public Map<String, Object> detail(long alarmId) {
        Alarm a = visibleAlarm(alarmId);
        MasterCache.Snapshot m = masterCache.get();
        Event e = a.mainEvent();
        CentralDto.Status status = central.status(a.processId());
        CentralDto.Unit unit = m.unit(a.unitId());
        Set<Long> unitSensors = new HashSet<>();
        if (unit != null) {
            unit.members().forEach(x -> unitSensors.add(x.sensorId()));
        }
        List<Map<String, Object>> sensors = new ArrayList<>();
        for (SensorStatus s : status.sensors()) {
            if (unitSensors.contains(s.sensorId())) {
                sensors.add(sensorRow(s, m));
            }
        }
        Map<String, Object> rule = null;
        Map<String, Object> trend = null;
        Double usual = null;
        String cause = AlarmViews.title(a);
        Equipment eq = null;
        if (e != null) {
            CentralDto.Sensor def = m.sensor(e.sensorId());
            int hold = def == null || def.threshold() == null ? 0
                    : (e.stage() >= 3 ? def.threshold().alarmSamples() : def.threshold().warnSamples()) * def.sampleSec();
            Double ruleWarn = e.lowSide() ? e.lowWarn() : e.warn();
            Double ruleAlarm = e.lowSide() ? e.lowAlarm() : e.alarm();
            rule = new LinkedHashMap<>();
            rule.put("sensorTag", e.tag());
            rule.put("warn", ruleWarn);
            rule.put("alarm", ruleAlarm);
            rule.put("holdSec", hold);
            // 기준 띠 끝 = 설비 상세 게이지와 같은 값 (경보 기준 × 1.3)
            rule.put("max", ruleAlarm == null ? null : Math.round(ruleAlarm * 1.3 * 10) / 10.0);
            // 평소 값 = 알림 직전 7일 평균 (10분 평균의 평균)
            usual = ThermalService.mean(central.series(List.of(e.sensorId()),
                    a.raisedAt().minusDays(7), a.raisedAt(), "10m"));
            eq = m.equipment(e.equipmentId());
            cause = (eq == null ? "" : eq.name() + " ") + Codes.sensorName(e.type()) + "이(가) "
                    + Codes.stageName(e.stage()) + " 기준(" + num(e.threshold()) + " " + nz(e.unit()) + ")을 "
                    + holdText(hold) + (e.lowSide() ? " 밑돌았습니다." : " 넘었습니다.");
            OffsetDateTime from = a.raisedAt().minusMinutes(30);
            OffsetDateTime to = time.now();
            CentralDto.Series series = central.series(List.of(e.sensorId()), from, to.isAfter(from) ? to : from.plusMinutes(31), "1m");
            List<List<Object>> points = new ArrayList<>();
            if (!series.series().isEmpty() && series.series().get(0).points() != null) {
                for (List<Object> p : series.series().get(0).points()) {
                    points.add(java.util.Arrays.asList(p.get(0), p.get(1)));
                }
            }
            trend = new LinkedHashMap<>();
            trend.put("sensorId", e.sensorId());
            trend.put("tag", e.tag());
            trend.put("unit", e.unit());
            trend.put("warn", ruleWarn);
            trend.put("alarm", ruleAlarm);
            trend.put("points", points);
        }
        Template template = e == null ? null
                : checklists.template(eq == null ? "" : eq.type(), e.type(), a.stage());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", a.alarmId());
        out.put("level", Codes.level(a.stage()));
        out.put("status", a.status());
        out.put("hazard", a.hazard());
        out.put("location", sitePrefix(a, m) + AlarmViews.location(a, m));
        out.put("raisedAt", a.raisedAt());
        out.put("cause", cause);
        out.put("usual", usual != null ? usual : (e == null ? null : e.peak()));
        out.put("rule", rule);
        out.put("sensors", sensors);
        // 열화상 그림(VMS)은 아직 없어서 IR 센서 값으로 요약 (imageUrl 은 null → 화면이 단계별 예시 그림 사용)
        out.put("thermal", thermal.forAlarm(a.processId(), eq == null ? null : eq.id(), status, time.now()));
        out.put("trend", trend);
        out.put("causeCandidates", template == null ? List.of() : List.of(template.cause()));
        out.put("templateId", template == null ? null : template.id());
        out.put("equipmentId", eq == null ? null : eq.id());
        out.put("equipmentType", eq == null ? null : eq.type());
        out.put("processId", a.processId());
        out.put("valueStillOver", a.valueStillOver());
        out.put("fanAuto", AlarmViews.fanAuto(a));
        return out;
    }

    /** GET /alarms/{alarmId}/response: 경보 창 (같은 공정의 센서 전부, 정지시킬 설비, 로그) */
    public Map<String, Object> response(long alarmId) {
        Alarm a = visibleAlarm(alarmId);
        MasterCache.Snapshot m = masterCache.get();
        CentralDto.Status status = central.status(a.processId());
        List<Map<String, Object>> sensors = new ArrayList<>();
        Long cameraId = null;
        for (SensorStatus s : status.sensors()) {
            sensors.add(sensorRow(s, m));
            if ("IR".equals(s.type()) && cameraId == null) {
                cameraId = s.sensorId();
            }
        }
        Map<Long, CentralDto.EquipmentStatus> eqStatus = new HashMap<>();
        status.equipment().forEach(x -> eqStatus.put(x.equipmentId(), x));
        List<Map<String, Object>> equipment = new ArrayList<>();
        for (Equipment eq : m.equipmentOf(a.processId())) {
            if ("CAMERA".equals(eq.type())) {
                continue;
            }
            CentralDto.EquipmentStatus st = eqStatus.get(eq.id());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", eq.id());
            row.put("name", eq.name());
            row.put("type", eq.type());
            row.put("tags", String.join(" · ", m.sensorsOf(eq.id()).stream().map(CentralDto.Sensor::tag).toList()));
            row.put("level", Codes.level(st == null ? 1 : st.stage()));
            row.put("runState", Codes.runState(st == null ? eq.runState() : st.runState()));
            equipment.add(row);
        }
        List<String> reasons = new ArrayList<>();
        List<String> details = new ArrayList<>();
        Set<Long> detailed = new HashSet<>();
        List<Map<String, Object>> logs = new ArrayList<>();
        for (Event e : a.events()) {
            String side = e.lowSide() ? "미달" : "초과";
            reasons.add(e.tag() + " " + num(e.peak()) + " " + nz(e.unit()) + "(" + Codes.stageName(e.stage()) + " " + num(e.threshold()) + ")");
            if (detailed.add(e.sensorId())) {
                details.add(reasonDetail(e, a.raisedAt()));
            }
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("at", e.startedAt());
            l.put("text", e.tag() + " " + Codes.stageName(e.stage()) + " 기준 " + side + " (" + num(e.peak()) + " " + nz(e.unit()) + ")");
            logs.add(l);
            if (e.endedAt() != null) {
                Map<String, Object> end = new LinkedHashMap<>();
                end.put("at", e.endedAt());
                end.put("text", e.tag() + " 기준 아래로 복귀");
                logs.add(end);
            }
        }
        List<Action> actions = checklists.actions(alarmId);
        for (Action act : actions) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("at", act.doneAt());
            l.put("text", ChecklistRepository.actionText(act) + (act.userName() == null ? "" : " (" + act.userName() + ")"));
            logs.add(l);
        }
        logs.sort((x, y) -> ((OffsetDateTime) y.get("at")).compareTo((OffsetDateTime) x.get("at")));
        Equipment mainEq = a.mainEvent() == null ? null : m.equipment(a.mainEvent().equipmentId());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", a.alarmId());
        out.put("level", Codes.level(a.stage()));
        out.put("status", a.status());
        out.put("hazard", a.hazard());
        out.put("fireId", null);
        // 유형 넣기 (docs/api/alarm-modal-hazard.md): '상도 건조로에서 가스 경보가 발생했습니다'
        out.put("title", a.unitName() + "에서 " + hazardPrefix(a.hazard()) + Codes.stageName(a.stage()) + "가 발생했습니다");
        out.put("location", sitePrefix(a, m) + AlarmViews.location(a, m));
        out.put("raisedAt", a.raisedAt());
        out.put("reason", String.join(", ", reasons));
        out.put("reasonDetail", String.join(" · ", details));
        out.put("cameraId", cameraId);
        // 열화상 그림(VMS)은 아직 없어서 IR 센서 값으로 요약 (imageUrl 은 null → 화면이 단계별 예시 그림 사용)
        out.put("thermal", thermal.forAlarm(a.processId(), mainEq == null ? null : mainEq.id(), status, time.now()));
        out.put("shutdown", shutdownView(actions, m.equipmentOf(a.processId()), eqStatus));
        out.put("sensors", sensors);
        out.put("equipment", equipment);
        out.put("logs", logs);
        out.put("valueStillOver", a.valueStillOver());
        out.put("fanAuto", AlarmViews.fanAuto(a));
        return out;
    }

    /**
     * GET /stats/alarms/{alarmId}: 통계·이력 알림 상세 패널 (stats-history-api.md 2장).
     * 지난 알림 · 진행 중 알림, 주의 · 경보 모두 같은 모양. 담당 범위 밖이면 404.
     */
    public Map<String, Object> historyDetail(long alarmId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        Alarm a;
        try {
            a = central.alarm(alarmId);
        } catch (ApiException e) {
            throw ApiException.notFound("ALARM_NOT_FOUND", "없는 알림입니다.");
        }
        MasterCache.Snapshot m = masterCache.get();
        if (!scope.canProcess(a.processId(), m)) {
            throw ApiException.notFound("ALARM_NOT_FOUND", "없는 알림입니다.");
        }
        Map<String, Object> row = items(List.of(a), m).get(0);
        OffsetDateTime now = time.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", a.alarmId());
        out.put("level", Codes.level(a.stage()));
        out.put("status", a.status());
        out.put("hazard", a.hazard());
        out.put("location", row.get("location"));
        out.put("factoryId", a.factoryId());
        out.put("processId", a.processId());
        out.put("equipmentId", row.get("equipmentId"));
        out.put("raisedAt", a.raisedAt());
        out.put("closedAt", a.closedAt());
        OffsetDateTime end = a.closedAt() != null ? a.closedAt() : now;
        out.put("actionMinutes", a.raisedAt() == null ? null
                : Math.max(1, java.time.Duration.between(a.raisedAt(), end).toMinutes()));
        out.put("sensorTag", row.get("sensorTag"));
        out.put("sensorType", row.get("sensorType"));
        out.put("value", row.get("value"));
        out.put("unit", row.get("unit"));
        out.put("threshold", row.get("threshold"));
        out.put("ackedBy", row.get("ackedBy"));
        out.put("fanAuto", AlarmViews.fanAuto(a));

        CentralDto.AlarmSeries s = central.alarmSeries(alarmId);
        Map<String, Object> series = new LinkedHashMap<>();
        series.put("from", s.from());
        series.put("to", s.to());
        series.put("intervalSec", s.intervalSec());
        List<Map<String, Object>> sensors = new ArrayList<>();
        for (CentralDto.AlarmSensorSeries x : s.sensors()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("sensorId", x.sensorId());
            r.put("tag", x.tag());
            r.put("type", Codes.sensorType(x.type()));
            r.put("unit", x.unit());
            r.put("warn", x.warn());
            r.put("alarm", x.alarm());
            r.put("lowWarn", x.lowWarn());
            r.put("lowAlarm", x.lowAlarm());
            r.put("trigger", x.trigger());
            r.put("points", x.points());
            sensors.add(r);
        }
        series.put("sensors", sensors);
        out.put("series", series);

        Map<String, Object> th = thermal.forAlarm(a.processId(),
                a.equipmentId(), central.status(a.processId()), now);
        if (th != null) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("imageUrl", th.get("imageUrl"));
            t.put("capturedAt", a.raisedAt());
            t.put("max", th.get("max"));
            t.put("avg", th.get("avg"));
            t.put("maxPoint", th.get("maxPoint"));
            t.put("cameraTag", th.get("cameraTag"));
            out.put("thermal", t);
        } else {
            out.put("thermal", null);
        }

        List<Map<String, Object>> timeline = new ArrayList<>();
        for (Object o : (List<?>) timeline(alarmId).get("timeline")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> st = (Map<String, Object>) o;
            String step = historyStep(String.valueOf(st.get("step")));
            if (step == null) {
                continue;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("at", st.get("at"));
            r.put("step", step);
            r.put("by", st.get("by"));
            r.put("text", st.get("text") != null ? st.get("text") : historyText(step, a, row));
            timeline.add(r);
        }
        timeline.sort(java.util.Comparator.comparing(r -> (OffsetDateTime) r.get("at"),
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        out.put("timeline", timeline);

        if (a.stage() < 3) {
            Checklist c = checklists.latestForAlarm(alarmId);
            if (c == null) {
                out.put("checklist", null);
            } else {
                Map<String, Object> ck = new LinkedHashMap<>();
                ck.put("assignee", c.assigneeName());
                ck.put("doneCount", c.doneCount());
                ck.put("totalCount", c.items().size());
                List<Map<String, Object>> items = new ArrayList<>();
                for (ChecklistRepository.Item it : c.items()) {
                    Map<String, Object> i = new LinkedHashMap<>();
                    i.put("seq", it.seq());
                    i.put("content", it.content());
                    i.put("doneAt", it.doneAt());
                    i.put("result", it.result());
                    i.put("memo", it.memo());
                    items.add(i);
                }
                ck.put("items", items);
                out.put("checklist", ck);
            }
            out.put("shutdown", null);
        } else {
            out.put("checklist", null);
            Map<Long, CentralDto.EquipmentStatus> eqStatus = new HashMap<>();
            central.status(a.processId()).equipment().forEach(x -> eqStatus.put(x.equipmentId(), x));
            Map<String, Object> sd = shutdownView(checklists.actions(alarmId), m.equipmentOf(a.processId()), eqStatus);
            out.put("shutdown", sd == null ? null : Map.of("steps", sd.get("steps")));
        }
        return out;
    }

    /** GAS → '가스 ', FIRE → '화재 ', 없으면 빈 글자 */
    static String hazardPrefix(String hazard) {
        return "GAS".equals(hazard) ? "가스 " : "FIRE".equals(hazard) ? "화재 " : "";
    }

    /** 조치 이력 단계 → 상세 패널 단계 (RAISED ACK CHECKLIST REPORTED SHUTDOWN CLOSED). 나머지는 빼지 않고 그대로 */
    static String historyStep(String step) {
        return switch (step) {
            case "REPORT" -> "REPORTED";
            case "CLOSE" -> "CLOSED";
            default -> step;
        };
    }

    private static String historyText(String step, Alarm a, Map<String, Object> row) {
        return switch (step) {
            case "RAISED" -> Codes.stageName(a.stage()) + " 발생"
                    + (row.get("sensorTag") == null ? "" : " · " + row.get("sensorTag") + " " + row.get("value")
                    + " " + (row.get("unit") == null ? "" : row.get("unit"))
                    + (row.get("threshold") == null ? "" : " (기준 " + row.get("threshold") + ")"));
            case "ACK" -> "알림 확인";
            case "CLOSED" -> "알림 해제";
            default -> step;
        };
    }

    /** POST /alarms/{alarmId}/ack — 공장 담당자만 */
    @Transactional
    public Map<String, Object> ack(long alarmId) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        Alarm a = visibleAlarm(alarmId, scope);
        Alarm updated;
        try {
            updated = central.ackAlarm(alarmId, scope.userId());
        } catch (ApiException e) {
            if ("ALARM_ALREADY_ACKED".equals(e.code()) && a.ackedBy() != null) {
                String by = checklists.userNames(List.of(a.ackedBy())).get(a.ackedBy());
                throw new ApiException(e.status(), e.code(), "다른 담당자가 먼저 확인했습니다.", Map.of("ackedBy", by == null ? "" : by));
            }
            throw e;
        }
        checklists.addAction(alarmId, "ACK", null, "OK", null, scope.userId(), time.now());
        String name = checklists.userNames(List.of(scope.userId())).get(scope.userId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", updated.alarmId());
        out.put("status", updated.status());
        out.put("ackedBy", name);
        out.put("ackedAt", updated.ackedAt());
        return out;
    }

    /**
     * POST /alarms/{alarmId}/close — 경보 종료. 공정 센서가 모두 기준 아래로 내려온 뒤에만 된다.
     * 주의는 보통 체크리스트 조치 확인(confirm)으로 닫히고, 이건 경보·직접 종료용.
     */
    @Transactional
    public Map<String, Object> close(long alarmId, String result, String followUp) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        visibleAlarm(alarmId, scope);
        Alarm closed = central.closeAlarm(alarmId, scope.userId());
        String detail = result == null ? null : result + (followUp == null || followUp.isBlank() ? "" : " / 후속: " + followUp);
        checklists.addAction(alarmId, "CLOSE", null, "OK", detail, scope.userId(), time.now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", closed.alarmId());
        out.put("status", closed.status());
        out.put("closedAt", closed.closedAt());
        return out;
    }

    /** GET /notifications/summary: 상단 바 숫자 */
    public Map<String, Object> summary() {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        List<Alarm> open = central.alarms(new AlarmQuery(time.now().minusDays(7), time.now().plusSeconds(1),
                "OPEN", null, null, null, null, null, 0, 500)).items();
        long unacked = open.stream().filter(a -> scope.canProcess(a.processId(), m)).count();
        int activeChecklists = 0;
        for (Checklist c : checklists.active(scope.isWorker() ? scope.userId() : null)) {
            if (scope.isWorker()) {
                activeChecklists++;
                continue;
            }
            try {
                Alarm a = central.alarm(c.alarmId());
                if (scope.canProcess(a.processId(), m)) {
                    activeChecklists++;
                }
            } catch (ApiException ignored) {
                // 중앙에서 지워진 경보 (리셋)
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("unackedAlarmCount", unacked);
        out.put("activeChecklistCount", activeChecklists);
        out.put("unreadChatCount", 0);  // 채팅은 아직
        out.put("activeFire", null);    // 화재 판정은 아직
        return out;
    }

    // ---------------------------------------------------------------

    /** 화면 목록 줄 여러 개 (확인자 이름, 체크리스트 진행 포함) */
    public List<Map<String, Object>> items(List<Alarm> alarms, MasterCache.Snapshot m) {
        List<Long> userIds = new ArrayList<>();
        List<Long> alarmIds = new ArrayList<>();
        for (Alarm a : alarms) {
            userIds.add(a.ackedBy());
            alarmIds.add(a.alarmId());
        }
        Map<Long, String> names = checklists.userNames(userIds);
        Map<Long, Checklist> byAlarm = new HashMap<>();
        for (Checklist c : checklists.list(null, null, alarmIds)) {
            byAlarm.putIfAbsent(c.alarmId(), c); // 최근 것 (sent_at DESC)
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Alarm a : alarms) {
            out.add(AlarmViews.item(a, m, names, byAlarm.get(a.alarmId())));
        }
        return out;
    }

    /** 경보를 읽고 담당 범위를 확인한다 */
    public Alarm visibleAlarm(long alarmId) {
        return visibleAlarm(alarmId, currentUser.scope());
    }

    private Alarm visibleAlarm(long alarmId, UserScope scope) {
        Alarm a = central.alarm(alarmId);
        if (!scope.canProcess(a.processId(), masterCache.get())) {
            throw ApiException.outOfScope();
        }
        return a;
    }

    private void checkScope(UserScope scope, Long siteId, Long factoryId, Long processId) {
        if (processId != null) {
            currentUser.checkProcess(scope, processId);
        } else if (factoryId != null) {
            currentUser.checkFactory(scope, factoryId);
        } else if (siteId != null) {
            currentUser.checkSite(scope, siteId);
        }
    }

    private Map<String, Object> sensorRow(SensorStatus s, MasterCache.Snapshot m) {
        Equipment eq = m.equipment(s.equipmentId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.sensorId());
        row.put("tag", s.tag());
        row.put("type", Codes.sensorType(s.type()));
        row.put("equipmentName", eq == null ? null : eq.name());
        row.put("value", s.value());
        row.put("unit", s.unit());
        row.put("warn", s.warn());
        row.put("alarm", s.alarm());
        row.put("lowWarn", s.lowWarn());
        row.put("lowAlarm", s.lowAlarm());
        row.put("level", Codes.level(s.stage()));
        return row;
    }

    private static Map<String, Object> shift(Map<Integer, int[]> byHour, int startHour, int endHourExclusive) {
        List<Map<String, Object>> hours = new ArrayList<>();
        int alarmCount = 0;
        int actionCount = 0;
        for (int h = startHour; h < endHourExclusive; h++) {
            int hour = h % 24;
            int[] c = byHour.get(hour);
            if (c == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hour", hour);
            row.put("warn", c[0]);
            row.put("alarm", c[1]);
            row.put("action", c[2]);
            hours.add(row);
            alarmCount += c[0] + c[1];
            actionCount += c[2];
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("alarmCount", alarmCount);
        out.put("actionCount", actionCount);
        out.put("hours", hours);
        return out;
    }

    private static Map<String, Object> step(String step, OffsetDateTime at, String by, String target, String detail) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", step);
        s.put("at", at);
        s.put("by", by);
        s.put("target", target);
        s.put("result", detail);
        return s;
    }

    /** 사유 아래 한 줄: 'TE-305 163.9 ℃ (평소의 117%)'. 평소 = 경보 직전 7일 평균 */
    private String reasonDetail(Event e, OffsetDateTime raisedAt) {
        Double usual = ThermalService.mean(central.series(List.of(e.sensorId()), raisedAt.minusDays(7), raisedAt, "10m"));
        String text = e.tag() + " " + num(e.peak()) + " " + nz(e.unit());
        if (usual != null && usual > 0 && e.peak() != null) {
            text += " (평소의 " + Math.round(e.peak() / usual * 100) + "%)";
        }
        return text;
    }

    /** 셧다운 단계 이름 (설비 응답이 단계별로 오지 않아 시작 시각 기준으로 보여 줌) */
    private static final List<String> SHUTDOWN_STEPS = List.of("버너 연료 차단", "컨베이어 정지", "배기팬 정지");

    /**
     * 경보 창 셧다운 진행: { equipmentIds, startedAt, steps: [{ label, at }], done, auto }. 셧다운 전이면 null.
     * <pre>
     * - 대상 = 이 경보로 셧다운 명령을 보낸 설비 (조치 이력 SHUTDOWN)
     * - done = 대상 설비가 모두 정지(STOPPED)
     * - 단계 시각: 1단계 = 명령 시각, 2단계 = +1초, 3단계 = 모두 정지된 뒤 (+3초, 중앙 시뮬레이션 완료 시점)
     * </pre>
     */
    private Map<String, Object> shutdownView(List<Action> actions, List<Equipment> processEquipment,
                                             Map<Long, CentralDto.EquipmentStatus> eqStatus) {
        OffsetDateTime started = null;
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (Action act : actions) {
            if (!"SHUTDOWN".equals(act.action())) {
                continue;
            }
            for (Equipment eq : processEquipment) {
                if (eq.name().equals(act.target())) {
                    ids.add(eq.id());
                }
            }
            if (started == null || act.doneAt().isBefore(started)) {
                started = act.doneAt();
            }
        }
        if (started == null) {
            return null;
        }
        boolean done = !ids.isEmpty() && ids.stream().allMatch(id -> {
            CentralDto.EquipmentStatus st = eqStatus.get(id);
            return st != null && "STOPPED".equals(st.runState());
        });
        OffsetDateTime now = time.now();
        List<Map<String, Object>> steps = new ArrayList<>();
        for (int i = 0; i < SHUTDOWN_STEPS.size(); i++) {
            OffsetDateTime at = i == SHUTDOWN_STEPS.size() - 1 ? (done ? started.plusSeconds(3) : null)
                    : started.plusSeconds(i);
            if (at != null && at.isAfter(now) && !done) {
                at = null;
            }
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("label", SHUTDOWN_STEPS.get(i));
            step.put("at", at);
            steps.add(step);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipmentIds", List.copyOf(ids));
        out.put("startedAt", started);
        out.put("steps", steps);
        out.put("done", done);
        out.put("auto", false);
        return out;
    }

    private static String sitePrefix(Alarm a, MasterCache.Snapshot m) {
        CentralDto.Plant p = m.plant(a.plantId());
        return p == null ? "" : p.name() + " · ";
    }

    private static String holdText(int sec) {
        if (sec <= 0) {
            return "";
        }
        return sec >= 60 ? (sec / 60) + "분 이상" : sec + "초 이상";
    }

    private static String num(Double v) {
        if (v == null) {
            return "-";
        }
        return v == Math.rint(v) ? String.valueOf(v.longValue()) : String.valueOf(Math.round(v * 100) / 100.0);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
