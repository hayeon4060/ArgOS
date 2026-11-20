package com.gte.argos.gateway.factory;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.file.FileStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 공장 관리 · 설비 재배치 (명세서 H1).
 * 사업장·공장·공정·설비·센서는 수집 서버 DB 에 있어 internal API 로 쓰고, 설비 담당자(equipment_manager)는 여기 DB 에 둔다.
 */
@Service
public class FactoryAdminService {

    static final long MAX_FLOORPLAN = 10L * 1024 * 1024;

    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final ActivityLogger activity;
    private final FileStore files;
    private final NamedParameterJdbcTemplate jdbc;

    public FactoryAdminService(CentralClient central, MasterCache masterCache, CurrentUser currentUser,
                               ActivityLogger activity, FileStore files, NamedParameterJdbcTemplate jdbc) {
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.activity = activity;
        this.files = files;
        this.jdbc = jdbc;
    }

    /** POST /sites (본사 관리자만) */
    public Map<String, Object> createSite(Map<String, Object> body) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN);
        Map<String, Object> out = central.createPlant(body);
        masterCache.invalidate();
        activity.log(scope.userId(), "FACTORY", ((Number) out.get("id")).longValue(), null, "plant",
                ((Number) out.get("id")).longValue(), "사업장 추가: " + body.get("name"));
        return out;
    }

    /** POST /factories */
    public Map<String, Object> createFactory(Map<String, Object> body) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        long siteId = num(body.get("siteId"), "siteId");
        currentUser.checkSite(scope, siteId);
        Map<String, Object> out = central.createFactory(body);
        masterCache.invalidate();
        activity.log(scope.userId(), "FACTORY", siteId, null, "factory", ((Number) out.get("id")).longValue(),
                "공장 추가: " + body.get("name"));
        return out;
    }

    /** POST /processes */
    public Map<String, Object> createProcess(Map<String, Object> body) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        long factoryId = num(body.get("factoryId"), "factoryId");
        currentUser.checkFactory(scope, factoryId);
        Map<String, Object> out = central.createProcess(body);
        masterCache.invalidate();
        activity.log(scope.userId(), "FACTORY", scope.siteId(), ((Number) out.get("id")).longValue(), "process",
                ((Number) out.get("id")).longValue(), "공정 추가: " + body.get("name"));
        return out;
    }

    /** PUT /processes/{processId}/layout: 한꺼번에 저장. 설비 담당자는 여기 DB 에 반영 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> saveLayout(long processId, Map<String, Object> body) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        currentUser.checkProcess(scope, processId);
        Map<String, Object> res = central.saveLayout(processId, body);
        for (Object o : (List<Object>) res.getOrDefault("managers", List.of())) {
            Map<String, Object> m = (Map<String, Object>) o;
            long equipmentId = ((Number) m.get("equipmentId")).longValue();
            List<Long> ids = new ArrayList<>();
            for (Object id : (List<Object>) m.get("managerIds")) {
                ids.add(((Number) id).longValue());
            }
            Object primary = m.get("primaryManagerId");
            setManagers(equipmentId, ids, primary instanceof Number n ? n.longValue() : (ids.isEmpty() ? null : ids.get(0)));
        }
        masterCache.invalidate();
        int changed = ((Number) res.getOrDefault("changedCount", 0)).intValue();
        activity.log(scope.userId(), "FACTORY", scope.siteId(), processId, "process", processId,
                "배치 저장 (" + changed + "건 변경)");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", res.get("version"));
        out.put("created", res.get("created"));
        out.put("changedCount", changed);
        // 지우지 않고 '사용 안 함'으로 바꾼 설비 id (센서·이력이 있는 설비)
        out.put("kept", res.getOrDefault("kept", java.util.List.of()));
        return out;
    }

    /** POST /processes/{processId}/floorplan (multipart) */
    public Map<String, Object> uploadFloorplan(long processId, byte[] bytes, String name, String mime, Double widthM,
                                               Double depthM) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        currentUser.checkProcess(scope, processId);
        String kind = imageKind(name, mime);
        if (bytes == null || bytes.length == 0 || kind == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TYPE_NOT_ALLOWED", "PNG · JPG · SVG 그림만 올릴 수 있습니다.");
        }
        if (bytes.length > MAX_FLOORPLAN) {
            throw new ApiException(HttpStatus.valueOf(413), "FILE_TOO_LARGE", "10MB 이하 그림만 올릴 수 있습니다.");
        }
        if (widthM == null || depthM == null || widthM <= 0 || depthM <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "실제 가로·세로 길이(m)를 넣어 주세요.");
        }
        FileStore.Stored f = files.save("FLOORPLAN", name, kind, bytes);
        String url = "/files/" + f.id();
        central.setFloorplan(processId, Map.of("url", url, "widthM", widthM, "depthM", depthM));
        masterCache.invalidate();
        activity.log(scope.userId(), "FACTORY", scope.siteId(), processId, "process", processId, "배치도 올림");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", f.id());
        out.put("url", url);
        out.put("widthM", widthM);
        out.put("depthM", depthM);
        return out;
    }

    /** 배치도 응답에 붙일 담당자 (설비 id → [{ id, name, primary }]) */
    public Map<Long, List<Map<String, Object>>> managersOf(List<Long> equipmentIds) {
        Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (equipmentIds.isEmpty()) {
            return out;
        }
        for (Map<String, Object> r : jdbc.queryForList("""
                SELECT m.equipment_id, m.user_id, m.is_primary, u.name FROM equipment_manager m
                JOIN app_user u ON u.user_id = m.user_id WHERE m.equipment_id IN (:ids)
                ORDER BY m.is_primary DESC, u.name
                """, new MapSqlParameterSource("ids", equipmentIds))) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", r.get("user_id"));
            v.put("name", r.get("name"));
            v.put("primary", r.get("is_primary"));
            out.computeIfAbsent(((Number) r.get("equipment_id")).longValue(), k -> new ArrayList<>()).add(v);
        }
        return out;
    }

    private void setManagers(long equipmentId, List<Long> userIds, Long primary) {
        jdbc.update("DELETE FROM equipment_manager WHERE equipment_id = :e", new MapSqlParameterSource("e", equipmentId));
        for (Long id : userIds) {
            jdbc.update("INSERT INTO equipment_manager (equipment_id, user_id, is_primary) VALUES (:e, :u, :p)",
                    new MapSqlParameterSource("e", equipmentId).addValue("u", id).addValue("p", id.equals(primary)));
        }
    }

    static String imageKind(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase();
        String m = mime == null ? "" : mime.toLowerCase();
        if (m.contains("png") || n.endsWith(".png")) {
            return "image/png";
        }
        if (m.contains("jpeg") || m.contains("jpg") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (m.contains("svg") || n.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return null;
    }

    private static long num(Object o, String field) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field + " 값이 필요합니다.");
    }
}
