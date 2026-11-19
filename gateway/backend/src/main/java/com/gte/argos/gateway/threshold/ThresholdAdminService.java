package com.gte.argos.gateway.threshold;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.CentralDto.Equipment;
import com.gte.argos.gateway.central.CentralDto.Sensor;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.checklist.ChecklistRepository;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 설비 기준값 관리 (API 명세서 F1) · 센서 설정 창 (H2).
 *
 * <pre>
 * 화면은 지속 조건을 초(warnHoldSec)로, 중앙 DB 는 샘플 수(warn_samples)로 다룬다. 샘플 = 초 ÷ 수집 주기(올림).
 * 저장은 본사 관리자 · 공장 담당자(담당 공장만). 저장하면 이용 로그(THRESHOLD)를 남긴다.
 * </pre>
 */
@Service
public class ThresholdAdminService {

    private static final List<String> SEASONS = List.of("ALL", "SPRING_FALL", "SUMMER", "WINTER");

    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final ChecklistRepository users;
    private final ActivityLogger activity;
    private final com.gte.argos.gateway.stream.StreamHub hub;

    public ThresholdAdminService(CentralClient central, MasterCache masterCache, CurrentUser currentUser,
                                 ChecklistRepository users, ActivityLogger activity,
                                 com.gte.argos.gateway.stream.StreamHub hub) {
        this.hub = hub;
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.users = users;
        this.activity = activity;
    }

    /** GET /equipment/tree?factoryId= — 왼쪽 트리 (공정 > 설비) */
    public List<Map<String, Object>> tree(long factoryId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        currentUser.checkFactory(scope, factoryId);
        MasterCache.Snapshot m = masterCache.get();
        CentralDto.Status status = central.status(null);
        Map<Long, Integer> stage = new HashMap<>();
        status.equipment().forEach(e -> stage.put(e.equipmentId(), e.stage()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (CentralDto.Process p : m.processesOf(factoryId)) {
            List<Map<String, Object>> equipment = new ArrayList<>();
            for (Equipment e : m.equipmentOf(p.id())) {
                int sensorCount = m.sensorsOf(e.id()).size();
                if (sensorCount == 0) {
                    continue; // 센서가 없는 설비는 기준값이 없다
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", e.id());
                row.put("name", e.name());
                row.put("type", e.type());
                row.put("sensorCount", sensorCount);
                row.put("level", Codes.level(stage.getOrDefault(e.id(), 1)));
                equipment.add(row);
            }
            Map<String, Object> pr = new LinkedHashMap<>();
            pr.put("processId", p.id());
            pr.put("processName", p.name());
            pr.put("equipment", equipment);
            out.add(pr);
        }
        return out;
    }

    /** GET /equipment/{id}/thresholds — 센서별 계절 기준 (지속 조건은 초) */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(long equipmentId) {
        Equipment eq = visibleEquipment(equipmentId, false);
        Map<String, Object> raw = central.equipmentThresholds(equipmentId);
        Map<Long, Double> values = currentValues(eq.processId());
        List<Map<String, Object>> sensors = new ArrayList<>();
        for (Map<String, Object> s : (List<Map<String, Object>>) raw.get("sensors")) {
            long sensorId = ((Number) s.get("sensorId")).longValue();
            int sampleSec = ((Number) s.get("sampleSec")).intValue();
            Map<String, Object> seasons = new LinkedHashMap<>();
            Map<String, String> notes = new HashMap<>();
            for (Map.Entry<String, Object> e : ((Map<String, Object>) s.get("seasons")).entrySet()) {
                Map<String, Object> r = (Map<String, Object>) e.getValue();
                seasons.put(e.getKey(), seasonView(r, sampleSec));
                notes.put(e.getKey(), (String) r.get("note"));
            }
            // 근거: 지금 계절 줄의 메모, 없으면 ALL 줄의 메모
            String basis = notes.getOrDefault(String.valueOf(raw.get("activeSeason")), notes.get("ALL"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sensorId", sensorId);
            row.put("tag", s.get("tag"));
            row.put("type", Codes.sensorType((String) s.get("type")));
            row.put("unit", s.get("unit"));
            row.put("value", values.get(sensorId));
            row.put("sampleSec", sampleSec);
            row.put("setpoint", s.get("setpoint"));
            row.put("seasons", seasons);
            row.put("legal", s.get("legal"));
            row.put("basis", basis);
            row.put("max", bandMax(seasons.get(String.valueOf(raw.get("activeSeason"))) != null
                    ? seasons.get(String.valueOf(raw.get("activeSeason"))) : seasons.get("ALL"), values.get(sensorId)));
            sensors.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipmentId", equipmentId);
        out.put("equipmentName", eq.name());
        out.put("equipmentType", eq.type());
        out.put("activeSeason", raw.get("activeSeason"));
        out.put("version", raw.get("version"));
        out.put("sensors", sensors);
        out.put("active", eq.active());
        out.put("fanAuto", eq.fanAuto());
        return out;
    }

    /**
     * PATCH /equipment/{id}/settings { active?, fanAuto? } — 바로 저장 (배치 판 번호는 올리지 않음).
     * 배기팬이 없는 설비에 fanAuto 가 오면 400 NO_FAN. 이용 로그 LAYOUT, SSE equipment.updated
     */
    public Map<String, Object> saveSettings(long equipmentId, SettingsRequest r) {
        Equipment eq = visibleEquipment(equipmentId, true);
        UserScope scope = currentUser.scope();
        if (r == null || (r.active() == null && r.fanAuto() == null)) {
            throw new IllegalArgumentException("active 또는 fanAuto");
        }
        if (r.fanAuto() != null && eq.fanAuto() == null) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "NO_FAN", "배기팬이 없는 설비입니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (r.active() != null) {
            body.put("active", r.active());
        }
        if (r.fanAuto() != null) {
            body.put("fanAuto", r.fanAuto());
        }
        Map<String, Object> res = central.equipmentSettings(equipmentId, body);
        masterCache.invalidate();
        List<String> changes = new ArrayList<>();
        if (r.active() != null && r.active() != eq.active()) {
            changes.add(r.active() ? "사용 상태 사용" : "사용 상태 사용 안 함");
        }
        if (r.fanAuto() != null && !r.fanAuto().equals(eq.fanAuto())) {
            changes.add(r.fanAuto() ? "배기팬 자동" : "배기팬 수동");
        }
        if (!changes.isEmpty()) {
            activity.log(scope.userId(), "LAYOUT", plantOf(eq), eq.processId(), "equipment", equipmentId,
                    eq.name() + " " + String.join(" · ", changes));
        }
        hub.publish("equipment.updated", Map.of("equipmentId", equipmentId, "processId", eq.processId()),
                s -> s.scope().canProcess(eq.processId(), masterCache.get()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipmentId", equipmentId);
        out.put("active", res.get("active"));
        out.put("fanAuto", res.get("fanAuto"));
        return out;
    }

    /** POST /thresholds/simulate — 저장하지 않고 최근 N일 알림 수만 계산 */
    public Map<String, Object> simulate(SimulateRequest r) {
        Sensor s = visibleSensor(r.sensorId(), false);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sensorId", r.sensorId());
        body.put("warn", r.warn());
        body.put("alarm", r.alarm());
        body.put("lowWarn", r.lowWarn());
        body.put("lowAlarm", r.lowAlarm());
        body.put("warnSamples", samples(r.warnHoldSec(), s.sampleSec()));
        body.put("alarmSamples", samples(r.alarmHoldSec(), s.sampleSec()));
        body.put("days", r.days() == null ? 7 : r.days());
        return central.simulateThreshold(body);
    }

    /** PUT /equipment/{id}/thresholds */
    @SuppressWarnings("unchecked")
    public Map<String, Object> save(long equipmentId, SaveRequest r) {
        Equipment eq = visibleEquipment(equipmentId, true);
        UserScope scope = currentUser.scope();
        if (r.season() == null || !SEASONS.contains(r.season())) {
            throw new IllegalArgumentException("season");
        }
        MasterCache.Snapshot m = masterCache.get();
        List<Map<String, Object>> sensors = new ArrayList<>();
        for (SensorThreshold t : r.sensors()) {
            Sensor s = m.sensor(t.sensorId());
            if (s == null || s.equipmentId() != equipmentId) {
                throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "SENSOR_NOT_IN_EQUIPMENT",
                        "이 설비의 센서가 아닙니다.");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sensorId", t.sensorId());
            row.put("warn", t.warn());
            row.put("alarm", t.alarm());
            row.put("lowWarn", t.lowWarn());
            row.put("lowAlarm", t.lowAlarm());
            row.put("warnSamples", samples(t.warnHoldSec(), s.sampleSec()));
            row.put("alarmSamples", samples(t.alarmHoldSec(), s.sampleSec()));
            sensors.add(row);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("season", r.season());
        body.put("version", r.version());
        body.put("reason", r.reason());
        body.put("userId", scope.userId());
        body.put("sensors", sensors);
        Map<String, Object> saved = central.saveThresholds(equipmentId, body);
        masterCache.invalidate();

        List<String> lines = new ArrayList<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) saved.getOrDefault("changes", List.of())) {
            Sensor s = m.sensor(((Number) c.get("sensorId")).longValue());
            lines.add((s == null ? "" : s.tag() + " ") + changeText((Map<String, Object>) c.get("before"),
                    (Map<String, Object>) c.get("after"), s == null ? "" : s.unit()));
        }
        activity.log(scope.userId(), "THRESHOLD", plantOf(eq), eq.processId(), "sensor_threshold", equipmentId,
                eq.name() + " " + String.join(", ", lines) + " (" + r.season() + ", " + r.reason() + ")");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipmentId", equipmentId);
        out.put("version", saved.get("version"));
        out.put("appliedNow", saved.get("appliedNow"));
        return out;
    }

    /** GET /thresholds/history?equipmentId=&from=&to=&sensorId=&season= */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> history(long equipmentId, LocalDate from, LocalDate to, Long sensorId,
                                             String season) {
        visibleEquipment(equipmentId, false);
        OffsetDateTime f = from == null ? null : from.atStartOfDay(com.gte.argos.gateway.central.SimTime.ZONE).toOffsetDateTime();
        OffsetDateTime t = to == null ? null : to.plusDays(1).atStartOfDay(com.gte.argos.gateway.central.SimTime.ZONE).toOffsetDateTime();
        List<Map<String, Object>> rows = central.thresholdHistory(equipmentId, sensorId, season, f, t);
        List<Long> userIds = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object by = r.get("changedBy");
            if (by != null) {
                userIds.add(((Number) by).longValue());
            }
        }
        Map<Long, String> names = users.userNames(userIds);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object by = r.get("changedBy");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.get("id"));
            row.put("sensorId", r.get("sensorId"));
            row.put("sensorTag", r.get("sensorTag"));
            row.put("season", r.get("season"));
            row.put("before", r.get("before"));
            row.put("after", r.get("after"));
            row.put("changedBy", by == null ? "초기 설정" : names.getOrDefault(((Number) by).longValue(), "알 수 없음"));
            row.put("changedAt", r.get("changedAt"));
            row.put("reason", r.get("reason"));
            out.add(row);
        }
        return out;
    }

    /** GET /sensors/{id} — 센서 설정 창 */
    public Map<String, Object> sensor(long sensorId) {
        Sensor s = visibleSensor(sensorId, false);
        Equipment eq = masterCache.get().equipment(s.equipmentId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", s.id());
        out.put("tag", s.tag());
        out.put("label", (eq == null ? "" : eq.name() + " ") + Codes.sensorName(s.type()));
        out.put("type", Codes.sensorType(s.type()));
        out.put("unit", s.unit());
        out.put("equipmentId", s.equipmentId());
        out.put("active", s.active());
        out.put("connection", s.connection());
        out.put("sampleSec", s.sampleSec());
        CentralDto.Threshold th = s.threshold();
        if (th != null) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("season", th.season());
            t.put("warn", th.warn());
            t.put("alarm", th.alarm());
            t.put("lowWarn", th.lowWarn());
            t.put("lowAlarm", th.lowAlarm());
            t.put("warnHoldSec", th.warnSamples() * s.sampleSec());
            t.put("alarmHoldSec", th.alarmSamples() * s.sampleSec());
            out.put("threshold", t);
        } else {
            out.put("threshold", null);
        }
        return out;
    }

    /** PUT /sensors/{id} — 수집 주기 · 사용 여부 · 기준값 (지금 적용 중인 계절 줄) */
    public Map<String, Object> updateSensor(long sensorId, SensorUpdateRequest r) {
        Sensor s = visibleSensor(sensorId, true);
        UserScope scope = currentUser.scope();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sampleSec", r.sampleSec());
        body.put("active", r.active());
        Map<String, Object> updated = central.updateSensor(sensorId, body);
        int sampleSec = r.sampleSec() != null ? r.sampleSec() : s.sampleSec();
        if (r.threshold() != null) {
            ThresholdInput t = r.threshold();
            String season = s.threshold() == null ? "ALL" : s.threshold().season();
            SaveRequest save = new SaveRequest(season, null,
                    r.reason() == null || r.reason().isBlank() ? "센서 설정 창에서 변경" : r.reason(),
                    List.of(new SensorThreshold(sensorId, t.warn(), t.alarm(), t.lowWarn(), t.lowAlarm(),
                            t.warnHoldSec(), t.alarmHoldSec())));
            Map<String, Object> saved = save(s.equipmentId(), save);
            updated.put("version", saved.get("version"));
        } else {
            masterCache.invalidate();
            Equipment eq = masterCache.get().equipment(s.equipmentId());
            activity.log(scope.userId(), "LAYOUT", eq == null ? null : plantOf(eq), eq == null ? null : eq.processId(),
                    "sensor", sensorId, s.tag() + " 수집 주기 " + sampleSec + "초"
                            + (r.active() == null ? "" : r.active() ? " · 사용" : " · 사용 안 함"));
        }
        return updated;
    }

    // ---------------------------------------------------------------

    private Equipment visibleEquipment(long equipmentId, boolean write) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        Equipment eq = masterCache.get().equipment(equipmentId);
        if (eq == null) {
            throw ApiException.notFound("EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        currentUser.checkProcess(scope, eq.processId());
        return eq;
    }

    private Sensor visibleSensor(long sensorId, boolean write) {
        Sensor s = masterCache.get().sensor(sensorId);
        if (s == null) {
            throw ApiException.notFound("SENSOR_NOT_FOUND", "없는 센서입니다.");
        }
        visibleEquipment(s.equipmentId(), write);
        return s;
    }

    private Map<Long, Double> currentValues(long processId) {
        Map<Long, Double> out = new HashMap<>();
        for (CentralDto.SensorStatus s : central.status(processId).sensors()) {
            out.put(s.sensorId(), s.value());
        }
        return out;
    }

    private Long plantOf(Equipment eq) {
        MasterCache.Snapshot m = masterCache.get();
        Long f = m.factoryOfProcess(eq.processId());
        return f == null ? null : m.plantOfFactory(f);
    }

    /** 기준 띠 끝 = (경보 · 주의 · 지금 값 중 가장 큰 값) × 1.25, 소수 첫째 자리. 셋 다 없으면 null */
    @SuppressWarnings("unchecked")
    static Double bandMax(Object season, Double current) {
        double top = current == null ? Double.NaN : current;
        if (season instanceof Map<?, ?> m) {
            for (String k : List.of("alarm", "warn")) {
                Object v = ((Map<String, Object>) m).get(k);
                if (v instanceof Number n && (Double.isNaN(top) || n.doubleValue() > top)) {
                    top = n.doubleValue();
                }
            }
        }
        if (Double.isNaN(top) || top <= 0) {
            return null;
        }
        return Math.round(top * 1.25 * 10) / 10.0;
    }

    private static Map<String, Object> seasonView(Map<String, Object> r, int sampleSec) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("warn", r.get("warn"));
        v.put("alarm", r.get("alarm"));
        v.put("lowWarn", r.get("lowWarn"));
        v.put("lowAlarm", r.get("lowAlarm"));
        v.put("warnHoldSec", ((Number) r.get("warnSamples")).intValue() * sampleSec);
        v.put("alarmHoldSec", ((Number) r.get("alarmSamples")).intValue() * sampleSec);
        v.put("validFrom", r.get("validFrom"));
        return v;
    }

    /** 초 → 샘플 수 (올림). 비우면 null = 지금 값 유지 */
    static Integer samples(Integer holdSec, int sampleSec) {
        if (holdSec == null) {
            return null;
        }
        return Math.max(1, (int) Math.ceil(holdSec / (double) Math.max(1, sampleSec)));
    }

    private static String changeText(Map<String, Object> before, Map<String, Object> after, String unit) {
        List<String> parts = new ArrayList<>();
        for (String[] k : new String[][] {{"warn", "주의"}, {"alarm", "경보"}, {"lowWarn", "하한 주의"}, {"lowAlarm", "하한 경보"}}) {
            Object b = before == null ? null : before.get(k[0]);
            Object a = after == null ? null : after.get(k[0]);
            if (a != null && !a.equals(b)) {
                parts.add(k[1] + " " + (b == null ? "-" : b) + " → " + a + (unit == null ? "" : " " + unit));
            }
        }
        return parts.isEmpty() ? "지속 조건 변경" : String.join(", ", parts);
    }

    // ---------------------------------------------------------------

    public record SimulateRequest(long sensorId, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                                  Integer warnHoldSec, Integer alarmHoldSec, Integer days) {
    }

    /**
     * @param season  ALL | SPRING_FALL | SUMMER | WINTER
     * @param version 조회 때 받은 version (다른 사람이 먼저 바꿨으면 409 THRESHOLD_CONFLICT)
     */
    public record SettingsRequest(Boolean active, Boolean fanAuto) {
    }

    public record SaveRequest(String season, Long version, String reason, List<SensorThreshold> sensors) {
    }

    public record SensorThreshold(long sensorId, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                                  Integer warnHoldSec, Integer alarmHoldSec) {
    }

    public record ThresholdInput(Double warn, Double alarm, Double lowWarn, Double lowAlarm, Integer warnHoldSec,
                                 Integer alarmHoldSec) {
    }

    public record SensorUpdateRequest(String label, Integer sampleSec, Boolean active, ThresholdInput threshold,
                                      String reason) {
    }
}
