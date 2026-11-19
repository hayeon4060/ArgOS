package com.gte.argos.factory.master;

import com.gte.argos.factory.common.api.ApiException;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.ingest.LiveJudgementService;
import com.gte.argos.factory.live.LiveFeed;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공장 관리 (명세서 H1) 중 마스터 쓰기: 사업장·공장·공정 추가, 배치 저장, 배치도 정보.
 * 연계(중앙) 서버만 부르는 internal API 뒤에 있다. 권한 검사는 중앙 서버가 하고 여기서는 데이터 규칙만 본다.
 */
@Service
public class MasterAdminService {

    private final JdbcTemplate jdbc;
    private final LiveJudgementService live;
    private final LiveFeed feed;
    private final DemoClock clock;

    public MasterAdminService(JdbcTemplate jdbc, LiveJudgementService live, LiveFeed feed, DemoClock clock) {
        this.jdbc = jdbc;
        this.live = live;
        this.feed = feed;
        this.clock = clock;
    }

    public Map<String, Object> createPlant(String name, String code, String address, Double lat, Double lng) {
        require(name, "name");
        require(code, "code");
        Long id = insert("INSERT INTO plant (name, code, address, lat, lng) VALUES (?, ?, ?, ?, ?) RETURNING plant_id",
                name, code, address, lat, lng);
        masterChanged("PLANT", id);
        return Map.of("id", id, "name", name);
    }

    public Map<String, Object> createFactory(long plantId, String code, String name) {
        require(name, "name");
        require(code, "code");
        Long id = insert("""
                INSERT INTO factory (plant_id, code, name, sort_order)
                VALUES (?, ?, ?, (SELECT COALESCE(MAX(sort_order), 0) + 1 FROM factory WHERE plant_id = ?))
                RETURNING factory_id
                """, plantId, code, name, plantId);
        masterChanged("FACTORY", id);
        return Map.of("id", id, "name", name);
    }

    public Map<String, Object> createProcess(long factoryId, String code, String name, String tagPrefix) {
        require(name, "name");
        require(code, "code");
        Long id = insert("INSERT INTO process (factory_id, code, name, tag_prefix) VALUES (?, ?, ?, ?) RETURNING process_id",
                factoryId, code, name, tagPrefix);
        masterChanged("PROCESS", id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", name);
        out.put("hasFloorplan", false);
        return out;
    }

    /** 배치 버전 · 배치도 · 설비 회전 (마스터 응답에 없는 것) */
    public Map<String, Object> layoutMeta(long processId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT layout_version, floorplan_url, floorplan_width_m, floorplan_depth_m FROM process WHERE process_id = ?
                """, processId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "공정이 없습니다.");
        }
        Map<String, Object> r = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", r.get("layout_version"));
        out.put("floorplan", r.get("floorplan_url") == null ? null : Map.of("url", r.get("floorplan_url"),
                "widthM", r.get("floorplan_width_m"), "depthM", r.get("floorplan_depth_m")));
        Map<String, Object> rot = new LinkedHashMap<>();
        for (Map<String, Object> e : jdbc.queryForList("SELECT equipment_id, rot FROM equipment WHERE process_id = ?", processId)) {
            rot.put(String.valueOf(e.get("equipment_id")), e.get("rot"));
        }
        out.put("rot", rot);
        return out;
    }

    public Map<String, Object> setFloorplan(long processId, String url, double widthM, double depthM) {
        int n = jdbc.update("""
                UPDATE process SET floorplan_url = ?, floorplan_width_m = ?, floorplan_depth_m = ? WHERE process_id = ?
                """, url, widthM, depthM, processId);
        if (n == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "공정이 없습니다.");
        }
        masterChanged("PROCESS", processId);
        return Map.of("processId", processId, "url", url, "widthM", widthM, "depthM", depthM);
    }

    /**
     * PUT 배치 저장. 버전이 다르면 409 LAYOUT_CONFLICT. 하나라도 틀리면 전부 되돌린다.
     * 설비·센서 '삭제'는 기록(추이·이벤트)이 남아 있으면 지우지 않고 사용 안 함으로 바꾼다.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveLayout(long processId, LayoutRequest req) {
        Integer current = jdbc.query("SELECT layout_version FROM process WHERE process_id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getInt(1) : null, processId);
        if (current == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "공정이 없습니다.");
        }
        if (req.version() == null || req.version() != current) {
            throw new ApiException(HttpStatus.CONFLICT, "LAYOUT_CONFLICT", "다른 사람이 먼저 저장했습니다. 다시 불러와 주세요.");
        }
        int changed = 0;
        Map<String, Long> createdEq = new LinkedHashMap<>();
        Map<String, Long> createdSensors = new LinkedHashMap<>();
        List<Map<String, Object>> managers = new ArrayList<>();
        Map<String, Object> eq = req.equipment() == null ? Map.of() : req.equipment();
        for (Map<String, Object> c : list(eq.get("create"))) {
            String name = str(c.get("name"));
            require(name, "equipment.name");
            Map<String, Object> size = map(c.get("size"));
            Map<String, Object> pos = map(c.get("pos"));
            Long id = insert("""
                    INSERT INTO equipment (process_id, code, name, equipment_type, shape, size_w, size_d, size_h,
                                           pos_x, pos_y, pos_z, rot)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING equipment_id
                    """, processId, code(c, processId), name, str(c.get("type")), str(c.get("shape")),
                    dbl(size.get("w")), dbl(size.get("d")), dbl(size.get("h")),
                    dbl(pos.get("x")), dbl(pos.get("y")), dbl(pos.get("z")), dblOr(c.get("rot"), 0));
            createdEq.put(str(c.get("tempId")), id);
            if (c.get("managerIds") != null) {
                managers.add(Map.of("equipmentId", id, "managerIds", c.get("managerIds"),
                        "primaryManagerId", c.getOrDefault("primaryManagerId", "")));
            }
            changed++;
        }
        for (Map<String, Object> u : list(eq.get("update"))) {
            long id = lng(u.get("id"));
            checkEquipment(id, processId);
            if (u.containsKey("pos")) {
                Map<String, Object> pos = map(u.get("pos"));
                jdbc.update("UPDATE equipment SET pos_x = ?, pos_y = ?, pos_z = ? WHERE equipment_id = ?",
                        dbl(pos.get("x")), dbl(pos.get("y")), dbl(pos.get("z")), id);
            }
            if (u.containsKey("rot")) {
                jdbc.update("UPDATE equipment SET rot = ? WHERE equipment_id = ?", dblOr(u.get("rot"), 0), id);
            }
            if (u.containsKey("size")) {
                Map<String, Object> size = map(u.get("size"));
                jdbc.update("UPDATE equipment SET size_w = ?, size_d = ?, size_h = ? WHERE equipment_id = ?",
                        dbl(size.get("w")), dbl(size.get("d")), dbl(size.get("h")), id);
            }
            if (u.containsKey("name")) {
                jdbc.update("UPDATE equipment SET name = ? WHERE equipment_id = ?", str(u.get("name")), id);
            }
            if (u.containsKey("active")) {
                jdbc.update("UPDATE equipment SET is_active = ? WHERE equipment_id = ?", Boolean.TRUE.equals(u.get("active")), id);
            }
            if (u.containsKey("fanAuto") && u.get("fanAuto") != null) {
                setFanAuto(id, Boolean.TRUE.equals(u.get("fanAuto")));
            }
            if (u.containsKey("managerIds")) {
                managers.add(Map.of("equipmentId", id, "managerIds", u.get("managerIds"),
                        "primaryManagerId", u.getOrDefault("primaryManagerId", "")));
            }
            changed++;
        }
        for (Object d : list(eq.get("delete"))) {
            long id = lng(d);
            checkEquipment(id, processId);
            removeEquipment(id);
            changed++;
        }
        Map<String, Object> sn = req.sensors() == null ? Map.of() : req.sensors();
        for (Map<String, Object> c : list(sn.get("create"))) {
            Object eqRef = c.get("equipmentId");
            Long equipmentId = eqRef instanceof Number n ? n.longValue() : createdEq.get(String.valueOf(eqRef));
            if (equipmentId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "센서의 설비를 찾을 수 없습니다: " + eqRef);
            }
            checkEquipment(equipmentId, processId);
            String tag = str(c.get("tag"));
            require(tag, "sensor.tag");
            Integer taken = jdbc.queryForObject("SELECT COUNT(*) FROM sensor WHERE tag = ?", Integer.class, tag);
            if (taken != null && taken > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "TAG_TAKEN", "이미 있는 센서 태그입니다: " + tag);
            }
            String type = dbType(str(c.get("type")));
            Long id = insert("""
                    INSERT INTO sensor (tag, equipment_id, sensor_type, unit, connection, sample_sec)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING sensor_id
                    """, tag, equipmentId, type, str(c.get("unit")), "IR".equals(type) ? "VMS" : "PLC",
                    c.get("sampleSec") == null ? 5 : ((Number) c.get("sampleSec")).intValue());
            createdSensors.put(tag, id);
            changed++;
        }
        for (Map<String, Object> u : list(sn.get("update"))) {
            long id = lng(u.get("id"));
            checkSensor(id, processId);
            if (u.containsKey("active")) {
                jdbc.update("UPDATE sensor SET is_active = ? WHERE sensor_id = ?", Boolean.TRUE.equals(u.get("active")), id);
            }
            if (u.containsKey("sampleSec")) {
                jdbc.update("UPDATE sensor SET sample_sec = ? WHERE sensor_id = ?", ((Number) u.get("sampleSec")).intValue(), id);
            }
            if (u.containsKey("unit")) {
                jdbc.update("UPDATE sensor SET unit = ? WHERE sensor_id = ?", str(u.get("unit")), id);
            }
            changed++;
        }
        for (Object d : list(sn.get("delete"))) {
            long id = lng(d);
            checkSensor(id, processId);
            Integer open = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM sensor_event e JOIN alarm_event ae ON ae.event_id = e.event_id
                    JOIN alarm a ON a.alarm_id = ae.alarm_id
                    WHERE e.sensor_id = ? AND a.status <> 'CLOSED'
                    """, Integer.class, id);
            if (open != null && open > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "SENSOR_HAS_OPEN_ALARM", "알림이 열려 있는 센서는 지울 수 없습니다.");
            }
            removeSensor(id);
            changed++;
        }
        jdbc.update("UPDATE process SET layout_version = layout_version + 1 WHERE process_id = ?", processId);
        live.reloadDefinitions();
        masterChanged("LAYOUT", processId);
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("equipment", createdEq);
        created.put("sensors", createdSensors);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", current + 1);
        out.put("created", created);
        out.put("changedCount", changed);
        out.put("managers", managers);
        return out;
    }

    /**
     * 설비 설정 바로 저장 (equipment-settings-api.md). 배치 판 번호(layout_version)는 올리지 않는다.
     *
     * @param active  사용 상태 (null 이면 그대로)
     * @param fanAuto 배기팬 자동 제어 (null 이면 그대로). 배기팬이 없는 설비면 400 NO_FAN
     */
    @Transactional
    public Map<String, Object> saveSettings(long equipmentId, Boolean active, Boolean fanAuto) {
        Long processId = jdbc.query("SELECT process_id FROM equipment WHERE equipment_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, equipmentId);
        if (processId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        if (fanAuto != null) {
            setFanAuto(equipmentId, fanAuto);
        }
        if (active != null) {
            jdbc.update("UPDATE equipment SET is_active = ? WHERE equipment_id = ?", active, equipmentId);
            live.reloadDefinitions();
        }
        masterChanged("EQUIPMENT", processId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("equipmentId", equipmentId);
        out.put("active", jdbc.queryForObject("SELECT is_active FROM equipment WHERE equipment_id = ?", Boolean.class, equipmentId));
        out.put("fanAuto", fanAutoOf(equipmentId));
        return out;
    }

    /** 배기팬 자체 또는 그 설비(관제 단위 대표 설비)에 달린 배기팬들 */
    List<Long> fansOf(long equipmentId) {
        return jdbc.queryForList("""
                SELECT e.equipment_id FROM equipment e WHERE e.equipment_id = ? AND e.equipment_type = 'FAN'
                UNION
                SELECT f.equipment_id FROM control_unit cu
                JOIN control_unit_sensor cus ON cus.unit_id = cu.unit_id
                JOIN sensor s ON s.sensor_id = cus.sensor_id
                JOIN equipment f ON f.equipment_id = s.equipment_id AND f.equipment_type = 'FAN'
                WHERE cu.equipment_id = ?
                """, Long.class, equipmentId, equipmentId);
    }

    private void setFanAuto(long equipmentId, boolean fanAuto) {
        List<Long> fans = fansOf(equipmentId);
        if (fans.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_FAN", "배기팬이 없는 설비입니다.");
        }
        for (Long fan : fans) {
            jdbc.update("UPDATE equipment SET fan_auto = ? WHERE equipment_id = ?", fanAuto, fan);
        }
    }

    private Boolean fanAutoOf(long equipmentId) {
        List<Long> fans = fansOf(equipmentId);
        if (fans.isEmpty()) {
            return null;
        }
        return jdbc.queryForObject("SELECT bool_and(fan_auto) FROM equipment WHERE equipment_id IN ("
                + String.join(",", fans.stream().map(String::valueOf).toList()) + ")", Boolean.class);
    }

    public record LayoutRequest(Integer version, Map<String, Object> equipment, Map<String, Object> sensors) {
    }

    // ------------------------------------------------------------------

    private void removeEquipment(long id) {
        Integer used = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sensor s WHERE s.equipment_id = ?
                """, Integer.class, id);
        if (used != null && used > 0) {
            jdbc.update("UPDATE equipment SET is_active = FALSE WHERE equipment_id = ?", id);
            jdbc.update("UPDATE sensor SET is_active = FALSE WHERE equipment_id = ?", id);
            return;
        }
        try {
            jdbc.update("DELETE FROM equipment WHERE equipment_id = ?", id);
        } catch (DataIntegrityViolationException e) {
            jdbc.update("UPDATE equipment SET is_active = FALSE WHERE equipment_id = ?", id);
        }
    }

    private void removeSensor(long id) {
        Integer history = jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM sensor_trend WHERE sensor_id = ?)
                     + (SELECT COUNT(*) FROM sensor_event WHERE sensor_id = ?)
                     + (SELECT COUNT(*) FROM control_unit_sensor WHERE sensor_id = ?)
                """, Integer.class, id, id, id);
        if (history != null && history > 0) {
            jdbc.update("UPDATE sensor SET is_active = FALSE WHERE sensor_id = ?", id);
            return;
        }
        jdbc.update("DELETE FROM sensor_threshold WHERE sensor_id = ?", id);
        jdbc.update("DELETE FROM sensor WHERE sensor_id = ?", id);
    }

    private void checkEquipment(long id, long processId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE equipment_id = ? AND process_id = ?",
                Integer.class, id, processId);
        if (n == null || n == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "이 공정의 설비가 아닙니다: " + id);
        }
    }

    private void checkSensor(long id, long processId) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sensor s JOIN equipment e ON e.equipment_id = s.equipment_id
                WHERE s.sensor_id = ? AND e.process_id = ?
                """, Integer.class, id, processId);
        if (n == null || n == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "이 공정의 센서가 아닙니다: " + id);
        }
    }

    private Long insert(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, Long.class, args);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "CODE_TAKEN", "같은 이름이나 코드가 이미 있습니다.");
        }
    }

    private String code(Map<String, Object> c, long processId) {
        String code = str(c.get("code"));
        if (code != null && !code.isBlank()) {
            return code;
        }
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM equipment WHERE process_id = ?", Integer.class, processId);
        return "EQ-" + processId + "-" + ((n == null ? 0 : n) + 1);
    }

    /** 화면 센서 종류 → DB 센서 종류 */
    static String dbType(String type) {
        if (type == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "센서 종류가 없습니다.");
        }
        return switch (type) {
            case "TEMP", "TE" -> "TE";
            case "CURRENT", "IT" -> "IT";
            case "CO", "AT" -> "AT";
            case "IR" -> "IR";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "센서 종류를 확인하세요: " + type);
        };
    }

    private void masterChanged(String what, Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("what", what);
        data.put("id", id);
        feed.append("MASTER_CHANGED", clock.nowOffset(), data);
    }

    private static void require(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " 값이 필요합니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return o instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static double dblOr(Object o, double d) {
        return o instanceof Number n ? n.doubleValue() : d;
    }

    private static long lng(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "id 를 확인하세요: " + o);
    }
}
