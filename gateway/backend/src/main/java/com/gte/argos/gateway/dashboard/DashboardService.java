package com.gte.argos.gateway.dashboard;

import com.gte.argos.gateway.factory.FactoryAdminService;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralClient.AlarmQuery;
import com.gte.argos.gateway.central.CentralClient.EventQuery;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.CentralDto.Equipment;
import com.gte.argos.gateway.central.CentralDto.EquipmentStatus;
import com.gte.argos.gateway.central.CentralDto.Event;
import com.gte.argos.gateway.central.CentralDto.Factory;
import com.gte.argos.gateway.central.CentralDto.Plant;
import com.gte.argos.gateway.central.CentralDto.Process;
import com.gte.argos.gateway.central.CentralDto.Sensor;
import com.gte.argos.gateway.central.CentralDto.SensorSeries;
import com.gte.argos.gateway.central.CentralDto.SensorStatus;
import com.gte.argos.gateway.central.CentralDto.Status;
import com.gte.argos.gateway.central.CentralDto.Threshold;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.checklist.ChecklistRepository;
import com.gte.argos.gateway.common.api.ApiException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 대시보드 화면 (API 명세서 A2, B1, B2, B3, B4).
 * 마스터·현재 상태·경보는 중앙 서버에서 HTTP 로 받아 화면 모양으로 바꾼다.
 */
@Service
public class DashboardService {

    /** 단계 집계에 쓰는 센서 종류 (열화상은 따로) */
    private static final List<String> LEVEL_TYPES = List.of("CURRENT", "TEMP", "CO");

    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final SimTime time;
    private final ChecklistRepository checklists;
    private final FactoryAdminService factoryAdmin;

    public DashboardService(CentralClient central, MasterCache masterCache, CurrentUser currentUser, SimTime time,
                            ChecklistRepository checklists, FactoryAdminService factoryAdmin) {
        this.factoryAdmin = factoryAdmin;
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.time = time;
        this.checklists = checklists;
    }

    // ---------------------------------------------------------------
    // A2 사업장 선택
    // ---------------------------------------------------------------

    /** GET /sites */
    public List<Map<String, Object>> sites() {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN);
        MasterCache.Snapshot m = masterCache.get();
        Status status = central.status(null);
        Map<Long, Integer> todayByPlant = todayAlarmCounts(null).byPlant;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Plant p : m.plants()) {
            if (!scope.canSite(p.id(), m)) {
                continue;
            }
            List<Factory> factories = m.factoriesOf(p.id());
            int processCount = 0;
            for (Factory f : factories) {
                processCount += m.processesOf(f.id()).size();
            }
            List<SensorStatus> sensors = sensorsOfPlant(status, m, p.id());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.id());
            row.put("code", p.code());
            row.put("name", p.name());
            row.put("address", p.address());
            row.put("lat", p.lat());
            row.put("lng", p.lng());
            row.put("level", Codes.level(status.level("plants", p.id())));
            row.put("factoryCount", factories.size());
            row.put("processCount", processCount);
            row.put("sensorCount", sensors.size());
            row.put("sensorLevels", levelCounts(sensors));
            row.put("todayAlarmCount", todayByPlant.getOrDefault(p.id(), 0));
            out.add(row);
        }
        return out;
    }

    /** GET /sites/{siteId}/tree */
    public Map<String, Object> siteTree(long siteId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN);
        MasterCache.Snapshot m = masterCache.get();
        plantOr404(m, siteId);
        currentUser.checkSite(scope, siteId);
        Status status = central.status(null);
        Map<Long, Integer> todayByFactory = todayAlarmCounts(siteId).byFactory;

        List<Map<String, Object>> factories = new ArrayList<>();
        for (Factory f : m.factoriesOf(siteId)) {
            List<Map<String, Object>> processes = new ArrayList<>();
            List<SensorStatus> factorySensors = new ArrayList<>();
            for (Process p : m.processesOf(f.id())) {
                List<SensorStatus> ps = sensorsOfProcess(status, p.id());
                factorySensors.addAll(ps);
                Map<String, Object> pr = new LinkedHashMap<>();
                pr.put("id", p.id());
                pr.put("name", p.name());
                pr.put("level", Codes.level(status.level("processes", p.id())));
                pr.put("abnormal", abnormal(ps));
                processes.add(pr);
            }
            Map<String, Object> fr = new LinkedHashMap<>();
            fr.put("id", f.id());
            fr.put("name", f.name());
            fr.put("level", Codes.level(status.level("factories", f.id())));
            fr.put("levelsByType", levelsByType(factorySensors));
            fr.put("todayAlarmCount", todayByFactory.getOrDefault(f.id(), 0));
            fr.put("processes", processes);
            factories.add(fr);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("siteId", siteId);
        out.put("factories", factories);
        return out;
    }

    // ---------------------------------------------------------------
    // B1 사업장 현황
    // ---------------------------------------------------------------

    /** GET /sites/{siteId}/overview */
    public Map<String, Object> siteOverview(long siteId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        MasterCache.Snapshot m = masterCache.get();
        Plant plant = plantOr404(m, siteId);
        currentUser.checkSite(scope, siteId);
        Status status = central.status(null);
        Map<Long, Integer> todayByFactory = todayAlarmCounts(siteId).byFactory;

        List<Map<String, Object>> factories = new ArrayList<>();
        List<SensorStatus> siteSensors = new ArrayList<>();
        for (Factory f : m.factoriesOf(siteId)) {
            List<SensorStatus> fs = new ArrayList<>();
            List<Map<String, Object>> processes = new ArrayList<>();
            for (Process p : m.processesOf(f.id())) {
                fs.addAll(sensorsOfProcess(status, p.id()));
                Map<String, Object> pr = new LinkedHashMap<>();
                pr.put("id", p.id());
                pr.put("name", p.name());
                pr.put("level", Codes.level(status.level("processes", p.id())));
                processes.add(pr);
            }
            siteSensors.addAll(fs);
            Map<String, Object> fr = new LinkedHashMap<>();
            fr.put("id", f.id());
            fr.put("code", f.code());
            fr.put("name", f.name());
            fr.put("level", Codes.level(status.level("factories", f.id())));
            fr.put("mapShape", f.mapShape());
            fr.put("levelsByType", levelsByType(fs));
            fr.put("sensorCount", fs.size());
            fr.put("todayAlarmCount", todayByFactory.getOrDefault(f.id(), 0));
            fr.put("accessible", scope.canFactory(f.id(), m));
            fr.put("processes", processes);
            factories.add(fr);
        }
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("id", plant.id());
        site.put("name", plant.name());
        site.put("level", Codes.level(status.level("plants", plant.id())));
        site.put("mapUrl", null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("site", site);
        out.put("levelsByType", levelsByType(siteSensors));
        out.put("factories", factories);
        return out;
    }

    /** GET /factories?siteId= */
    public List<Map<String, Object>> factories(long siteId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        MasterCache.Snapshot m = masterCache.get();
        currentUser.checkSite(scope, siteId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Factory f : m.factoriesOf(siteId)) {
            if (!scope.canFactory(f.id(), m)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", f.id());
            row.put("code", f.code());
            row.put("name", f.name());
            out.add(row);
        }
        return out;
    }

    /** GET /processes?factoryId= */
    public List<Map<String, Object>> processes(long factoryId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        MasterCache.Snapshot m = masterCache.get();
        currentUser.checkFactory(scope, factoryId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Process p : m.processesOf(factoryId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.id());
            row.put("code", p.code());
            row.put("name", p.name());
            row.put("hasFloorplan", hasFloorplan(m, p));
            row.put("equipmentCount", m.equipmentOf(p.id()).size());
            out.add(row);
        }
        return out;
    }

    // ---------------------------------------------------------------
    // B2 공장 현황
    // ---------------------------------------------------------------

    /** GET /factories/{factoryId}/overview */
    public Map<String, Object> factoryOverview(long factoryId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        MasterCache.Snapshot m = masterCache.get();
        Factory factory = m.factory(factoryId);
        if (factory == null) {
            throw ApiException.notFound("FACTORY_NOT_FOUND", "없는 공장입니다.");
        }
        currentUser.checkFactory(scope, factoryId);
        Status status = central.status(null);
        Map<Long, Integer> equipmentStage = new HashMap<>();
        for (EquipmentStatus e : status.equipment()) {
            equipmentStage.put(e.equipmentId(), e.stage());
        }

        List<Map<String, Object>> processes = new ArrayList<>();
        List<Map<String, Object>> cameras = new ArrayList<>();
        for (Process p : m.processesOf(factoryId)) {
            List<SensorStatus> ps = sensorsOfProcess(status, p.id());
            Map<String, Integer> equipmentLevels = zeroCounts();
            for (Equipment e : m.equipmentOf(p.id())) {
                equipmentLevels.merge(Codes.level(equipmentStage.getOrDefault(e.id(), 1)), 1, Integer::sum);
            }
            Double thermalMax = null;
            for (SensorStatus s : ps) {
                if ("IR".equals(s.type()) && s.value() != null) {
                    thermalMax = thermalMax == null ? s.value() : Math.max(thermalMax, s.value());
                    Equipment cam = m.equipment(s.equipmentId());
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", s.sensorId());
                    c.put("tag", s.tag());
                    c.put("processId", p.id());
                    c.put("x", cam == null ? null : cam.posX());
                    c.put("y", cam == null ? null : cam.posY());
                    cameras.add(c);
                }
            }
            Map<String, Object> pr = new LinkedHashMap<>();
            pr.put("id", p.id());
            pr.put("code", p.code());
            pr.put("name", p.name());
            pr.put("level", Codes.level(status.level("processes", p.id())));
            pr.put("equipmentCount", m.equipmentOf(p.id()).size());
            pr.put("sensorCount", ps.size());
            pr.put("levelsByType", levelsByType(ps));
            pr.put("equipmentLevels", equipmentLevels);
            pr.put("abnormal", abnormal(ps));
            pr.put("thermalMax", thermalMax);
            pr.put("hasFloorplan", hasFloorplan(m, p));
            processes.add(pr);
        }
        Plant plant = m.plant(factory.plantId());
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("id", factory.id());
        f.put("name", factory.name());
        f.put("level", Codes.level(status.level("factories", factory.id())));
        f.put("siteId", factory.plantId());
        f.put("siteName", plant == null ? null : plant.name());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("factory", f);
        out.put("processes", processes);
        out.put("cameras", cameras);
        return out;
    }

    // ---------------------------------------------------------------
    // B3 공정 배치도
    // ---------------------------------------------------------------

    /**
     * 공정 흐름 순서 (화면 아래 설비 줄 번호). DB 에 순서 칸이 없어 관제 단위 대표 설비(전처리 · 전착 탱크 · 건조로 …)를
     * 관제 단위 번호 순으로 1, 2, … 로 매긴다. 펌프 · 배기팬 · 버너 · 카메라는 null
     */
    static Map<Long, Integer> flowOrder(MasterCache.Snapshot m, long processId) {
        Map<Long, Integer> out = new java.util.HashMap<>();
        int n = 1;
        for (CentralDto.Unit u : m.units().stream().filter(u -> u.processId() == processId && u.equipmentId() != null)
                .sorted(java.util.Comparator.comparingLong(CentralDto.Unit::id)).toList()) {
            if (!out.containsKey(u.equipmentId())) {
                out.put(u.equipmentId(), n++);
            }
        }
        return out;
    }

    /** GET /processes/{processId}/layout */
    public Map<String, Object> processLayout(long processId) {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        Process p = processOr404(m, processId);
        currentUser.checkProcess(scope, processId);
        Map<String, Object> meta = layoutMeta(processId);
        @SuppressWarnings("unchecked")
        Map<String, Object> rot = (Map<String, Object>) meta.getOrDefault("rot", Map.of());
        List<Long> ids = new ArrayList<>();
        for (Equipment e : m.equipmentOf(processId)) {
            ids.add(e.id());
        }
        Map<Long, List<Map<String, Object>>> managers = factoryAdmin.managersOf(ids);
        Map<Long, Integer> seq = flowOrder(m, processId);
        List<Map<String, Object>> equipment = new ArrayList<>();
        List<Map<String, Object>> sensors = new ArrayList<>();
        for (Equipment e : m.equipmentOf(processId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.id());
            row.put("code", e.code());
            row.put("name", e.name());
            row.put("type", e.type());
            row.put("shape", e.shape());
            row.put("size", xyz("w", e.sizeW(), "d", e.sizeD(), "h", e.sizeH()));
            row.put("pos", xyz("x", e.posX(), "y", e.posY(), "z", e.posZ()));
            row.put("rot", rot.getOrDefault(String.valueOf(e.id()), 0));
            row.put("active", e.active());
            row.put("seq", seq.get(e.id()));
            row.put("fanAuto", e.fanAuto());
            row.put("managers", managers.getOrDefault(e.id(), List.of()));
            equipment.add(row);
            for (Sensor s : m.sensorsOf(e.id())) {
                Map<String, Object> sr = new LinkedHashMap<>();
                sr.put("id", s.id());
                sr.put("tag", s.tag());
                sr.put("type", Codes.sensorType(s.type()));
                sr.put("unit", s.unit());
                sr.put("equipmentId", e.id());
                sr.put("pos", xyz("x", e.posX(), "y", e.posY(), null, null));
                sr.put("active", s.active());
                Threshold th = s.threshold();
                sr.put("limitType", th == null ? null : Codes.limitType(th.warn(), th.alarm(), th.lowWarn(), th.lowAlarm()));
                sensors.add(sr);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("processId", p.id());
        out.put("name", p.name());
        out.put("version", meta.getOrDefault("version", 1));
        out.put("floorplan", meta.get("floorplan"));
        out.put("equipment", equipment);
        out.put("sensors", sensors);
        return out;
    }

    /** 배치 버전 · 배치도 · 회전 (수집 서버). 못 받으면 기본값 */
    private Map<String, Object> layoutMeta(long processId) {
        try {
            return central.layoutMeta(processId);
        } catch (RuntimeException e) {
            return Map.of("version", 1);
        }
    }

    /** GET /processes/{processId}/status */
    public Map<String, Object> processStatus(long processId) {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        processOr404(m, processId);
        currentUser.checkProcess(scope, processId);
        Status status = central.status(processId);
        Map<Long, Long> activeChecklist = checklists.activeChecklistByEquipment(openAlarmEquipment(processId));

        // 관제 단위별 단계 (backend-feedback-1120 5장 3번): 단위 = 대표 설비 + 구성 센서가 달린 설비들
        Map<Long, Long> equipmentOfSensor = new java.util.HashMap<>();
        for (SensorStatus s : status.sensors()) {
            equipmentOfSensor.put(s.sensorId(), s.equipmentId());
        }
        List<Map<String, Object>> units = new ArrayList<>();
        Map<Long, Integer> unitStageOfEquipment = new java.util.HashMap<>();
        for (CentralDto.Unit u : m.units()) {
            if (u.processId() != processId || !u.active()) {
                continue;
            }
            int stage = status.level("units", u.id());
            java.util.Set<Long> eqIds = new java.util.LinkedHashSet<>();
            if (u.equipmentId() != null) {
                eqIds.add(u.equipmentId());
            }
            for (CentralDto.UnitMember mem : u.members()) {
                Long eqId = equipmentOfSensor.get(mem.sensorId());
                if (eqId != null) {
                    eqIds.add(eqId);
                }
            }
            for (Long eqId : eqIds) {
                unitStageOfEquipment.merge(eqId, stage, Math::max);
            }
            Map<String, Object> ur = new LinkedHashMap<>();
            ur.put("id", u.id());
            ur.put("name", u.name());
            ur.put("equipmentId", u.equipmentId());
            ur.put("level", Codes.level(stage));
            ur.put("equipmentIds", eqIds);
            units.add(ur);
        }

        List<Map<String, Object>> equipment = new ArrayList<>();
        for (EquipmentStatus e : status.equipment()) {
            Equipment eq = m.equipment(e.equipmentId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.equipmentId());
            row.put("level", Codes.level(e.stage()));
            // 이 설비가 속한 관제 단위 중 가장 높은 단계 (배기팬만 경보여도 건조로 단위는 경보)
            row.put("unitLevel", Codes.level(Math.max(e.stage(), unitStageOfEquipment.getOrDefault(e.equipmentId(), 1))));
            row.put("runState", Codes.runState(e.runState()));
            row.put("fan", eq != null && "FAN".equals(eq.type()) ? fan(e) : null);
            row.put("activeChecklistId", activeChecklist.get(e.equipmentId()));
            equipment.add(row);
        }
        List<Map<String, Object>> sensors = new ArrayList<>();
        for (SensorStatus s : status.sensors()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.sensorId());
            row.put("tag", s.tag());
            row.put("value", s.value());
            row.put("level", Codes.level(s.stage()));
            row.put("warn", s.warn());
            row.put("alarm", s.alarm());
            row.put("lowWarn", s.lowWarn());
            row.put("lowAlarm", s.lowAlarm());
            row.put("limitType", Codes.limitType(s.warn(), s.alarm(), s.lowWarn(), s.lowAlarm()));
            sensors.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("processId", processId);
        out.put("level", Codes.level(status.level("processes", processId)));
        out.put("at", status.at());
        out.put("equipment", equipment);
        out.put("sensors", sensors);
        out.put("units", units);
        return out;
    }

    // ---------------------------------------------------------------
    // B4 설비 상세
    // ---------------------------------------------------------------

    /** GET /equipment/{equipmentId} */
    public Map<String, Object> equipment(long equipmentId) {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        Equipment e = equipmentOr404(m, equipmentId);
        currentUser.checkProcess(scope, e.processId());
        Status status = central.status(e.processId());

        int stage = 1;
        String runState = e.runState();
        for (EquipmentStatus es : status.equipment()) {
            if (es.equipmentId() == equipmentId) {
                stage = es.stage();
                runState = es.runState();
            }
        }
        List<Map<String, Object>> sensors = new ArrayList<>();
        Long cameraId = null;
        for (SensorStatus s : status.sensors()) {
            if ("IR".equals(s.type()) && cameraId == null) {
                cameraId = s.sensorId(); // 같은 공정의 열화상 카메라
            }
            if (s.equipmentId() != equipmentId) {
                continue;
            }
            Sensor def = m.sensor(s.sensorId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.sensorId());
            row.put("tag", s.tag());
            row.put("type", Codes.sensorType(s.type()));
            row.put("label", e.name() + " " + Codes.sensorName(s.type()));
            row.put("unit", s.unit());
            row.put("value", s.value());
            row.put("level", Codes.level(s.stage()));
            row.put("min", 0);
            row.put("max", gaugeMax(s));
            row.put("warn", s.warn());
            row.put("alarm", s.alarm());
            row.put("lowWarn", s.lowWarn());
            row.put("lowAlarm", s.lowAlarm());
            row.put("limitType", Codes.limitType(s.warn(), s.alarm(), s.lowWarn(), s.lowAlarm()));
            row.put("season", def == null || def.threshold() == null ? null : def.threshold().season());
            sensors.add(row);
        }
        Process p = m.process(e.processId());
        Factory f = p == null ? null : m.factory(p.factoryId());
        Plant pl = f == null ? null : m.plant(f.plantId());
        List<String> path = new ArrayList<>();
        if (pl != null) {
            path.add(pl.name());
        }
        if (f != null) {
            path.add(f.name());
        }
        if (p != null) {
            path.add(p.name());
        }
        Long activeChecklistId = checklists.activeChecklistByEquipment(openAlarmEquipment(e.processId())).get(equipmentId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", e.id());
        out.put("code", e.code());
        out.put("name", e.name());
        out.put("type", e.type());
        out.put("path", path);
        out.put("level", Codes.level(stage));
        out.put("runState", Codes.runState(runState));
        out.put("sensors", sensors);
        out.put("cameraId", cameraId);
        out.put("activeChecklist", activeChecklistId == null ? null : checklists.summary(activeChecklistId));
        return out;
    }

    /**
     * GET /equipment/{equipmentId}/events
     *
     * @param type ALARM | ACTION | THRESHOLD (비우면 전부)
     */
    public List<Map<String, Object>> equipmentEvents(long equipmentId, String date, String type) {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        Equipment e = equipmentOr404(m, equipmentId);
        currentUser.checkProcess(scope, e.processId());
        OffsetDateTime[] range = time.dayRange(date);
        List<Map<String, Object>> out = new ArrayList<>();

        if (type == null || "ALARM".equals(type)) {
            List<Event> events = central.events(new EventQuery(range[0], range[1], null, equipmentId, null, null, 500));
            for (Event ev : events) {
                String side = ev.lowSide() ? "미달" : "초과";
                Map<String, Object> start = new LinkedHashMap<>();
                start.put("at", ev.startedAt());
                start.put("type", "ALARM");
                start.put("level", Codes.level(ev.stage()));
                start.put("text", Codes.sensorName(ev.type()) + " " + Codes.stageName(ev.stage()) + " 기준 " + side);
                start.put("detail", fmt(ev.peak()) + " " + nz(ev.unit()) + " (기준 " + fmt(ev.threshold()) + " " + nz(ev.unit()) + ")");
                start.put("sensorTag", ev.tag());
                out.add(start);
                if (ev.endedAt() != null) {
                    Map<String, Object> end = new LinkedHashMap<>();
                    end.put("at", ev.endedAt());
                    end.put("type", "ALARM");
                    end.put("level", "NORMAL");
                    end.put("text", Codes.sensorName(ev.type()) + " " + Codes.stageName(ev.stage()) + " 해제");
                    end.put("sensorTag", ev.tag());
                    out.add(end);
                }
            }
        }
        if (type == null || "ACTION".equals(type)) {
            out.addAll(checklists.actionsForEquipmentAlarms(alarmIdsOfEquipment(equipmentId, range), range));
        }
        out.sort(Comparator.comparing((Map<String, Object> x) -> (OffsetDateTime) x.get("at")).reversed());
        return out;
    }

    /**
     * GET /sensors/series
     *
     * @param compare "yesterday" 면 같은 구간의 전날 값을 compare 에 담는다
     */
    public Map<String, Object> series(List<Long> ids, OffsetDateTime from, OffsetDateTime to, String step, String compare) {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        for (Long id : ids) {
            Long processId = m.processOfSensor(id);
            if (processId == null) {
                throw ApiException.notFound("SENSOR_NOT_FOUND", "없는 센서입니다.");
            }
            currentUser.checkProcess(scope, processId);
        }
        CentralDto.Series now = central.series(ids, from, to, step);
        Map<Long, List<List<Object>>> before = new HashMap<>();
        if ("yesterday".equalsIgnoreCase(compare) && from != null && to != null) {
            CentralDto.Series y = central.series(ids, from.minusDays(1), to.minusDays(1), now.step());
            for (SensorSeries s : y.series()) {
                before.put(s.sensorId(), valuePoints(s.points()));
            }
        }
        List<Map<String, Object>> series = new ArrayList<>();
        for (SensorSeries s : now.series()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sensorId", s.sensorId());
            row.put("tag", s.tag());
            row.put("unit", s.unit());
            row.put("warn", s.warn());
            row.put("alarm", s.alarm());
            row.put("lowWarn", s.lowWarn());
            row.put("lowAlarm", s.lowAlarm());
            row.put("limitType", Codes.limitType(s.warn(), s.alarm(), s.lowWarn(), s.lowAlarm()));
            row.put("points", valuePoints(s.points()));
            if (before.containsKey(s.sensorId())) {
                row.put("compare", before.get(s.sensorId()));
            }
            series.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("step", now.step());
        out.put("series", series);
        return out;
    }

    // ---------------------------------------------------------------
    // 도움 함수
    // ---------------------------------------------------------------

    /** 오늘 경보 수 (사업장별 · 공장별) */
    TodayCounts todayAlarmCounts(Long plantId) {
        OffsetDateTime[] r = time.dayRange(null);
        CentralDto.Page<CentralDto.Alarm> page = central.alarms(
                new AlarmQuery(r[0], r[1], null, null, null, null, null, plantId, 0, 500));
        TodayCounts c = new TodayCounts();
        for (CentralDto.Alarm a : page.items()) {
            c.byPlant.merge(a.plantId(), 1, Integer::sum);
            c.byFactory.merge(a.factoryId(), 1, Integer::sum);
        }
        return c;
    }

    static final class TodayCounts {
        final Map<Long, Integer> byPlant = new HashMap<>();
        final Map<Long, Integer> byFactory = new HashMap<>();
    }

    /** 공정의 열린 경보: alarmId → 대표 설비 */
    private Map<Long, Long> openAlarmEquipment(long processId) {
        CentralDto.Page<CentralDto.Alarm> page = central.alarms(new AlarmQuery(
                time.now().minusDays(7), time.now().plusSeconds(1), "OPEN,ACKED,ACTING",
                null, null, processId, null, null, 0, 100));
        Map<Long, Long> out = new HashMap<>();
        for (CentralDto.Alarm a : page.items()) {
            Event main = a.mainEvent();
            out.put(a.alarmId(), main != null ? main.equipmentId() : a.equipmentId());
        }
        return out;
    }

    private List<Long> alarmIdsOfEquipment(long equipmentId, OffsetDateTime[] range) {
        Equipment e = masterCache.get().equipment(equipmentId);
        CentralDto.Page<CentralDto.Alarm> page = central.alarms(new AlarmQuery(
                range[0], range[1], null, null, null, e.processId(), null, null, 0, 200));
        List<Long> ids = new ArrayList<>();
        for (CentralDto.Alarm a : page.items()) {
            for (Event ev : a.events()) {
                if (ev.equipmentId() == equipmentId) {
                    ids.add(a.alarmId());
                    break;
                }
            }
        }
        return ids;
    }

    private static List<SensorStatus> sensorsOfProcess(Status status, long processId) {
        List<SensorStatus> out = new ArrayList<>();
        for (SensorStatus s : status.sensors()) {
            if (s.processId() == processId) {
                out.add(s);
            }
        }
        return out;
    }

    private static List<SensorStatus> sensorsOfPlant(Status status, MasterCache.Snapshot m, long plantId) {
        List<SensorStatus> out = new ArrayList<>();
        for (SensorStatus s : status.sensors()) {
            Long f = m.factoryOfProcess(s.processId());
            Long p = f == null ? null : m.plantOfFactory(f);
            if (p != null && p == plantId) {
                out.add(s);
            }
        }
        return out;
    }

    /** { CURRENT: {NORMAL, WARN, ALARM}, TEMP: …, CO: … } */
    private static Map<String, Map<String, Integer>> levelsByType(List<SensorStatus> sensors) {
        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        for (String t : LEVEL_TYPES) {
            out.put(t, zeroCounts());
        }
        for (SensorStatus s : sensors) {
            Map<String, Integer> c = out.get(Codes.sensorType(s.type()));
            if (c != null) {
                c.merge(Codes.level(s.stage()), 1, Integer::sum);
            }
        }
        return out;
    }

    private static Map<String, Integer> levelCounts(List<SensorStatus> sensors) {
        Map<String, Integer> c = zeroCounts();
        for (SensorStatus s : sensors) {
            c.merge(Codes.level(s.stage()), 1, Integer::sum);
        }
        return c;
    }

    private static Map<String, Integer> zeroCounts() {
        Map<String, Integer> c = new LinkedHashMap<>();
        c.put("NORMAL", 0);
        c.put("WARN", 0);
        c.put("ALARM", 0);
        return c;
    }

    private static List<Map<String, Object>> abnormal(List<SensorStatus> sensors) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SensorStatus s : sensors) {
            if (s.stage() >= 2) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sensorId", s.sensorId());
                row.put("tag", s.tag());
                row.put("type", Codes.sensorType(s.type()));
                row.put("value", s.value());
                row.put("unit", s.unit());
                row.put("level", Codes.level(s.stage()));
                out.add(row);
            }
        }
        return out;
    }

    private static Map<String, Object> fan(EquipmentStatus e) {
        int rpm = e.fanRpm() == null ? 0 : e.fanRpm();
        Map<String, Object> fan = new LinkedHashMap<>();
        fan.put("power", rpm > 0 && "RUN".equals(e.runState()) ? "ON" : "OFF");
        fan.put("rpm", rpm);
        fan.put("targetRpm", rpm);
        return fan;
    }

    private static Map<String, Object> xyz(String a, Double av, String b, Double bv, String c, Double cv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(a, av);
        m.put(b, bv);
        if (c != null) {
            m.put(c, cv);
        }
        return m;
    }

    private static boolean hasFloorplan(MasterCache.Snapshot m, Process p) {
        // 바닥 그림 파일이 없어도 설비가 있으면 3D 배치도를 그릴 수 있다
        return p.floorplanFileId() != null || !m.equipmentOf(p.id()).isEmpty();
    }

    /** 게이지 최대값: 경보 기준의 1.3배 정도 */
    private static Double gaugeMax(SensorStatus s) {
        Double base = s.alarm() != null ? s.alarm() : s.value();
        return base == null ? null : Math.ceil(base * 1.3);
    }

    /** [시각, 평균, 최소, 최대] → [시각, 값] */
    private static List<List<Object>> valuePoints(List<List<Object>> points) {
        List<List<Object>> out = new ArrayList<>();
        if (points == null) {
            return out;
        }
        for (List<Object> p : points) {
            if (p.size() >= 2) {
                out.add(java.util.Arrays.asList(p.get(0), p.get(1)));
            }
        }
        return out;
    }

    private static Plant plantOr404(MasterCache.Snapshot m, long id) {
        Plant p = m.plant(id);
        if (p == null) {
            throw ApiException.notFound("SITE_NOT_FOUND", "없는 사업장입니다.");
        }
        return p;
    }

    private static Process processOr404(MasterCache.Snapshot m, long id) {
        Process p = m.process(id);
        if (p == null) {
            throw ApiException.notFound("PROCESS_NOT_FOUND", "없는 공정입니다.");
        }
        return p;
    }

    private static Equipment equipmentOr404(MasterCache.Snapshot m, long id) {
        Equipment e = m.equipment(id);
        if (e == null) {
            throw ApiException.notFound("EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        return e;
    }

    static String fmt(Double v) {
        if (v == null) {
            return "-";
        }
        return v == Math.rint(v) ? String.valueOf(v.longValue()) : String.valueOf(Math.round(v * 100) / 100.0);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
