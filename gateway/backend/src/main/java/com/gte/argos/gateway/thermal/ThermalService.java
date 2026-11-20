package com.gte.argos.gateway.thermal;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.common.api.ApiException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 열화상 최신 값.
 *
 * <pre>
 * - 실제 열화상 그림(VMS)은 아직 없어서 imageUrl 은 null 이다. 화면은 level 로 예시 그림을 고른다.
 * - max 는 IR 센서의 지금 값(최고 온도)이다. min·avg 는 그림이 없어 알 수 없으므로 null.
 * - 카메라가 보는 설비 = IR 센서가 속한 관제 단위의 설비들. 그 밖의 설비는 값 없이 정상 그림만 준다.
 * - level 은 보고 있는 설비의 지금 단계 (설비 상세에서 주의면 주의 그림).
 * </pre>
 */
@Service
public class ThermalService {

    private static final Map<String, Object> SCALE = Map.of("min", 20, "max", 120);
    private static final Map<String, Object> MAX_POINT = Map.of("x", 0.62, "y", 0.13);

    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;

    public ThermalService(CentralClient central, MasterCache masterCache, CurrentUser currentUser) {
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
    }

    /** 화면에서 쓰는 열화상 사진 주소 (중앙 file_asset id). 없으면 null */
    public static String imageUrl(Long centralFileId) {
        return centralFileId == null ? null : "/api/v1/thermal/images/" + centralFileId;
    }

    /** GET /thermal/images/{fileId}: 중앙 서버에 저장된 VMS 열화상 사진 */
    public org.springframework.http.ResponseEntity<byte[]> image(long fileId) {
        currentUser.scope();
        return central.file(fileId);
    }

    public Map<String, Object> latest(long cameraId, Long equipmentId) {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        CentralDto.Sensor ir = m.sensor(cameraId);
        if (ir == null || !"IR".equals(ir.type())) {
            throw ApiException.notFound("CAMERA_NOT_FOUND", "없는 열화상 카메라입니다.");
        }
        Long processId = m.processOfSensor(cameraId);
        if (processId == null) {
            throw ApiException.notFound("CAMERA_NOT_FOUND", "없는 열화상 카메라입니다.");
        }
        currentUser.checkProcess(scope, processId);

        CentralDto.Status status = central.status(processId);
        CentralDto.SensorStatus now = status.sensors().stream()
                .filter(s -> s.sensorId() == cameraId).findFirst().orElse(null);

        Set<Long> covered = coveredEquipment(m, cameraId, ir.equipmentId());
        boolean inView = equipmentId == null || covered.contains(equipmentId);
        int stage = 1;
        if (inView) {
            for (CentralDto.EquipmentStatus e : status.equipment()) {
                if (equipmentId == null ? covered.contains(e.equipmentId()) : e.equipmentId() == equipmentId) {
                    stage = Math.max(stage, e.stage());
                }
            }
            if (now != null) {
                stage = Math.max(stage, now.stage());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cameraId", cameraId);
        out.put("tag", ir.tag());
        out.put("imageUrl", null);
        out.put("streamUrl", ir.streamUrl());
        out.put("capturedAt", now == null ? null : now.valueAt());
        out.put("level", Codes.level(stage));
        out.put("max", inView && now != null ? now.value() : null);
        out.put("min", null);
        out.put("avg", null);
        out.put("maxPoint", inView ? MAX_POINT : null);
        out.put("scale", SCALE);
        out.put("zone", inView ? zone(m, covered) : null);
        return out;
    }

    /**
     * 알림 창(주의 상세 · 센서·열화상 확인)에 넣을 열화상 요약. 공정에 IR 센서가 없으면 null.
     * <pre>
     * { cameraId, cameraTag, imageUrl(null), level, max, avg, usualAtMax, maxPoint, scale, zoneName }
     * max = IR 지금 값, avg = 최근 1시간 평균, usualAtMax = 지난 7일 평균 (그림이 없어 IR 센서 값으로 대신)
     * </pre>
     */
    public Map<String, Object> forAlarm(long processId, Long equipmentId, CentralDto.Status status,
                                        java.time.OffsetDateTime now) {
        MasterCache.Snapshot m = masterCache.get();
        CentralDto.Sensor ir = m.sensorsOfProcess(processId).stream()
                .filter(s -> "IR".equals(s.type())).findFirst().orElse(null);
        if (ir == null) {
            return null;
        }
        CentralDto.SensorStatus cur = status.sensors().stream()
                .filter(s -> s.sensorId() == ir.id()).findFirst().orElse(null);
        CentralDto.Series hour = central.series(java.util.List.of(ir.id()), now.minusHours(1), now, "1m");
        Double avg = mean(hour);
        // 최고 = 최근 1시간 최댓값과 지금 값 중 큰 것 (평균보다 작아지지 않게)
        Double max = peak(hour);
        if (cur != null && cur.value() != null && (max == null || cur.value() > max)) {
            max = cur.value();
        }
        Double usual = mean(central.series(java.util.List.of(ir.id()), now.minusDays(7), now, "10m"));

        Set<Long> covered = coveredEquipment(m, ir.id(), ir.equipmentId());
        int stage = 1;
        for (CentralDto.EquipmentStatus e : status.equipment()) {
            if (covered.contains(e.equipmentId()) && (equipmentId == null || covered.contains(equipmentId))) {
                stage = Math.max(stage, e.stage());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cameraId", ir.id());
        out.put("cameraTag", ir.tag());
        out.put("imageUrl", null);
        out.put("streamUrl", ir.streamUrl());
        out.put("level", Codes.level(stage));
        out.put("max", max);
        out.put("avg", avg != null ? avg : max);
        out.put("usualAtMax", usual != null ? usual : max);
        out.put("maxPoint", MAX_POINT);
        out.put("scale", SCALE);
        out.put("zoneName", "배기팬 모터부");
        return out;
    }

    /** 추이 점들의 평균 (points: [시각, 평균, 최소, 최대]). 값이 없으면 null */
    public static Double mean(CentralDto.Series series) {
        if (series == null || series.series() == null || series.series().isEmpty()
                || series.series().get(0).points() == null) {
            return null;
        }
        double sum = 0;
        int n = 0;
        for (java.util.List<Object> p : series.series().get(0).points()) {
            if (p.size() > 1 && p.get(1) instanceof Number v) {
                sum += v.doubleValue();
                n++;
            }
        }
        return n == 0 ? null : Math.round(sum / n * 10) / 10.0;
    }

    /** 추이 점들의 최댓값 (points: [시각, 평균, 최소, 최대]). 값이 없으면 null */
    static Double peak(CentralDto.Series series) {
        if (series == null || series.series() == null || series.series().isEmpty()
                || series.series().get(0).points() == null) {
            return null;
        }
        Double max = null;
        for (java.util.List<Object> p : series.series().get(0).points()) {
            Object v = p.size() > 3 ? p.get(3) : p.size() > 1 ? p.get(1) : null;
            if (v instanceof Number n && (max == null || n.doubleValue() > max)) {
                max = n.doubleValue();
            }
        }
        return max;
    }

    /** IR 센서가 들어 있는 관제 단위의 설비들 (+ 카메라 자신) */
    private static Set<Long> coveredEquipment(MasterCache.Snapshot m, long cameraId, long cameraEquipmentId) {
        Set<Long> out = new HashSet<>();
        out.add(cameraEquipmentId);
        for (CentralDto.Unit u : m.units()) {
            boolean hasCamera = u.members().stream().anyMatch(x -> x.sensorId() == cameraId);
            if (!hasCamera) {
                continue;
            }
            if (u.equipmentId() != null) {
                out.add(u.equipmentId());
            }
            for (CentralDto.UnitMember x : u.members()) {
                CentralDto.Sensor s = m.sensor(x.sensorId());
                if (s != null) {
                    out.add(s.equipmentId());
                }
            }
        }
        return out;
    }

    private static Map<String, Object> zone(MasterCache.Snapshot m, Set<Long> covered) {
        StringBuilder desc = new StringBuilder();
        for (Long id : covered) {
            CentralDto.Equipment e = m.equipment(id);
            if (e == null || "CAMERA".equals(e.type())) {
                continue;
            }
            if (!desc.isEmpty()) {
                desc.append(" · ");
            }
            desc.append(e.name());
        }
        Map<String, Object> z = new LinkedHashMap<>();
        z.put("name", "배기팬 모터부");
        z.put("desc", desc.toString());
        return z;
    }
}
