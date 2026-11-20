package com.gte.argos.gateway.fire;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.auth.AuthRepository;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.CentralDto.Alarm;
import com.gte.argos.gateway.central.CentralDto.Event;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.checklist.ChecklistRepository;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.push.BroadcastSender;
import com.gte.argos.gateway.stream.StreamHub;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 화재 대응 (명세서 C4F · C5).
 *
 * <p>화재 판정: 위험 종류가 화재(FIRE)인 경보가 3단계이고, 그 근거에 열화상(IR) 센서 이벤트가 있으면 화재로 본다.
 * 중앙(수집) 서버 경보 피드를 보는 {@code FeedPoller} 가 경보가 열리거나 바뀔 때마다 {@link #onAlarm} 을 부른다.
 * 화재가 되면 경고 방송을 시작하고(방송 시스템 연동 전까지는 로그), 본사 통보 시각을 남기고, SSE fire.started 를 보낸다.
 */
@Service
public class FireService {

    static final int MAX_FAIL = 5;
    private static final Logger log = LoggerFactory.getLogger(FireService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final AuthRepository auth;
    private final PasswordEncoder passwords;
    private final ChecklistRepository checklists;
    private final ActivityLogger activity;
    private final BroadcastSender broadcast;
    private final com.gte.argos.gateway.push.PushSender push;
    private final StreamHub hub;
    private final SimTime time;

    public FireService(NamedParameterJdbcTemplate jdbc, CentralClient central, MasterCache masterCache,
                       CurrentUser currentUser, AuthRepository auth, PasswordEncoder passwords,
                       ChecklistRepository checklists, ActivityLogger activity, BroadcastSender broadcast,
                       StreamHub hub, SimTime time, com.gte.argos.gateway.push.PushSender push) {
        this.push = push;
        this.jdbc = jdbc;
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.auth = auth;
        this.passwords = passwords;
        this.checklists = checklists;
        this.activity = activity;
        this.broadcast = broadcast;
        this.hub = hub;
        this.time = time;
    }

    /** 화재 판정 규칙. 테스트하기 쉽게 static */
    static boolean isFire(Alarm a) {
        if (a == null || !"FIRE".equals(a.hazard()) || a.stage() < 3 || "CLOSED".equals(a.status())) {
            return false;
        }
        return irEvent(a) != null;
    }

    static Event irEvent(Alarm a) {
        if (a.events() == null) {
            return null;
        }
        Event best = null;
        for (Event e : a.events()) {
            if ("IR".equals(e.type()) && (best == null || num(e.peak()) > num(best.peak()))) {
                best = e;
            }
        }
        return best;
    }

    /** 경보가 열리거나 바뀔 때 (FeedPoller). 이미 화재로 기록된 경보는 건너뛴다 */
    public void onAlarm(Alarm a) {
        if (!isFire(a) || exists(a.alarmId())) {
            return;
        }
        Event ir = irEvent(a);
        MasterCache.Snapshot m = masterCache.get();
        CentralDto.Equipment eq = a.equipmentId() == null ? null : m.equipment(a.equipmentId());
        OffsetDateTime now = time.now();
        OffsetDateTime detected = ir.startedAt() != null ? ir.startedAt() : a.raisedAt();
        Long fireId = jdbc.queryForObject("""
                INSERT INTO fire_incident (alarm_id, plant_id, process_id, equipment_id, pos_x, pos_y, max_temp,
                                           detected_at, hq_notified_at)
                VALUES (:alarm, :plant, :process, :eq, :x, :y, :max, :detected, :now)
                ON CONFLICT (alarm_id) DO NOTHING
                RETURNING fire_id
                """, new MapSqlParameterSource("alarm", a.alarmId()).addValue("plant", a.plantId())
                .addValue("process", a.processId()).addValue("eq", a.equipmentId())
                .addValue("x", eq == null ? null : eq.posX()).addValue("y", eq == null ? null : eq.posY())
                .addValue("max", ir.peak()).addValue("detected", detected).addValue("now", now), Long.class);
        if (fireId == null) {
            return;
        }
        String message = broadcastMessage(a.processId(), m);
        boolean ok = broadcast.start(fireId, a.plantId(), a.processId(), message);
        if (ok) {
            jdbc.update("UPDATE fire_incident SET broadcast_started_at = :now WHERE fire_id = :id",
                    new MapSqlParameterSource("now", now).addValue("id", fireId));
        }
        checklists.addAction(a.alarmId(), "BROADCAST", "공장 경고 방송", ok ? "OK" : "FAIL", message, null, now);
        pushWorkers(a.processId(), "FIRE", fireId);
        log.info("화재 판정: fire={} alarm={} max={}", fireId, a.alarmId(), ir.peak());

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("fireId", fireId);
        ev.put("alarmId", a.alarmId());
        ev.put("siteId", a.plantId());
        ev.put("factoryId", a.factoryId());
        ev.put("processId", a.processId());
        ev.put("location", location(a.factoryId(), a.processId(), a.equipmentId(), m, false));
        ev.put("maxTemp", ir.peak());
        ev.put("detectedAt", detected);
        hub.publish("fire.started", ev, s -> s.scope().isHq() || s.scope().canSite(a.plantId(), masterCache.get()));
    }

    /** GET /fires?status=ACTIVE|CLEARED&siteId= */
    public List<Map<String, Object>> list(String status, Long siteId) {
        UserScope scope = currentUser.scope();
        MasterCache.Snapshot m = masterCache.get();
        MapSqlParameterSource p = new MapSqlParameterSource("status", status == null || status.isBlank() ? "ACTIVE" : status)
                .addValue("site", siteId);
        List<Fire> fires = jdbc.query("""
                SELECT * FROM fire_incident WHERE status = :status AND (CAST(:site AS BIGINT) IS NULL OR plant_id = :site)
                ORDER BY detected_at DESC LIMIT 100
                """, p, (rs, i) -> fire(rs));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Fire f : fires) {
            if (!scope.canSite(f.plantId(), m)) {
                continue;
            }
            CentralDto.Plant plant = m.plant(f.plantId());
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", f.id());
            v.put("status", f.status());
            v.put("siteId", f.plantId());
            v.put("siteName", plant == null ? null : plant.name());
            v.put("location", location(factoryOf(f, m), f.processId(), f.equipmentId(), m, false));
            v.put("lat", plant == null ? null : plant.lat());
            v.put("lng", plant == null ? null : plant.lng());
            v.put("detectedAt", f.detectedAt());
            v.put("maxTemp", f.maxTemp());
            out.add(v);
        }
        return out;
    }

    /** 상단 바: 내 범위에서 진행 중인 화재 하나 (가장 최근) {id, location}. 없으면 null */
    public Map<String, Object> activeFire() {
        List<Map<String, Object>> active = list("ACTIVE", null);
        if (active.isEmpty()) {
            return null;
        }
        Map<String, Object> f = active.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", f.get("id"));
        out.put("location", f.get("location"));
        return out;
    }

    /** GET /fires/{fireId} */
    public Map<String, Object> detail(long fireId) {
        UserScope scope = currentUser.scope();
        Fire f = find(fireId);
        currentUser.checkSite(scope, f.plantId());
        MasterCache.Snapshot m = masterCache.get();
        Alarm a = null;
        try {
            a = central.alarm(f.alarmId());
        } catch (RuntimeException e) {
            log.debug("화재 경보 조회 실패: {}", e.getMessage());
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", f.id());
        v.put("alarmId", f.alarmId());
        v.put("status", f.status());
        v.put("location", location(factoryOf(f, m), f.processId(), f.equipmentId(), m, true));
        v.put("siteId", f.plantId());
        v.put("factoryId", factoryOf(f, m));
        v.put("processId", f.processId());
        v.put("equipmentId", f.equipmentId());
        v.put("pos", f.posX() == null ? null : Map.of("x", f.posX(), "y", f.posY()));
        v.put("detectedAt", f.detectedAt());
        v.put("basis", a == null ? null : basis(a));
        Event ir = a == null ? null : irEvent(a);
        Map<String, Object> thermal = new LinkedHashMap<>();
        thermal.put("cameraId", ir == null ? null : ir.sensorId());
        // VMS 실시간 영상 주소 (ERD V6 sensor.stream_url). 3단계면 화면이 이 주소로 바로 연결
        CentralDto.Sensor camera = ir != null ? m.sensor(ir.sensorId())
                : m.sensorsOfProcess(f.processId()).stream().filter(x -> "IR".equals(x.type())).findFirst().orElse(null);
        thermal.put("cameraTag", camera == null ? null : camera.tag());
        thermal.put("streamUrl", camera == null ? null : camera.streamUrl());
        thermal.put("imageUrl", a == null ? null : com.gte.argos.gateway.thermal.ThermalService.imageUrl(a.thermalFileId()));
        thermal.put("max", f.maxTemp());
        thermal.put("min", null);
        thermal.put("avg", null);
        v.put("thermal", thermal);
        Map<String, Object> bc = new LinkedHashMap<>();
        bc.put("on", f.broadcastStartedAt() != null && f.broadcastEndedAt() == null);
        bc.put("startedAt", f.broadcastStartedAt());
        bc.put("message", broadcastMessage(f.processId(), m));
        v.put("broadcast", bc);
        v.put("hqNotifiedAt", f.hqNotifiedAt());
        v.put("unlock", unlock(f.unlockFailCount()));
        v.put("actions", actions(f));
        v.put("values", values(f, a, m));
        v.put("serverTime", time.now());
        return v;
    }

    /**
     * 감지 근거 숫자 3칸 { thermalMax, temp, co }. 열화상 최고는 화재 기록 값, 온도·CO 는 그 관제 단위 센서의 지금 값
     * (경보 원인 이벤트가 있으면 그 최고값). 센서가 없으면 null
     */
    private Map<String, Object> values(Fire f, Alarm a, MasterCache.Snapshot m) {
        Double temp = null;
        Double co = null;
        Double ir = f.maxTemp();
        java.util.Set<Long> unitSensors = new java.util.HashSet<>();
        if (a != null && m.unit(a.unitId()) != null) {
            m.unit(a.unitId()).members().forEach(x -> unitSensors.add(x.sensorId()));
        }
        if (f.processId() != null) {
            try {
                for (CentralDto.SensorStatus s : central.status(f.processId()).sensors()) {
                    if (!unitSensors.isEmpty() && !unitSensors.contains(s.sensorId()) || s.value() == null) {
                        continue;
                    }
                    switch (s.type()) {
                        case "TE" -> temp = temp == null ? s.value() : Math.max(temp, s.value());
                        case "AT" -> co = co == null ? s.value() : Math.max(co, s.value());
                        case "IR" -> ir = ir == null ? s.value() : ir;
                        default -> { }
                    }
                }
            } catch (RuntimeException e) {
                log.debug("화재 근거 값 조회 실패: {}", e.getMessage());
            }
        }
        if (a != null) {
            for (Event e : a.events()) {
                if (e.peak() == null) {
                    continue;
                }
                if ("TE".equals(e.type())) {
                    temp = temp == null ? e.peak() : Math.max(temp, e.peak());
                } else if ("AT".equals(e.type())) {
                    co = co == null ? e.peak() : Math.max(co, e.peak());
                } else if ("IR".equals(e.type())) {
                    ir = ir == null ? e.peak() : Math.max(ir, e.peak());
                }
            }
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("thermalMax", ir);
        v.put("temp", temp);
        v.put("co", co);
        return v;
    }

    /** POST /fires/{fireId}/clear: 공장 담당자가 자기 비밀번호를 다시 넣어 해제 */
    public Map<String, Object> clear(long fireId, String password, String memo) {
        UserScope scope = currentUser.scope();
        if (scope.isHq()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACTION_NOT_ALLOWED", "본사 관리자는 화재 경보를 해제하지 않습니다.");
        }
        scope = currentUser.require(UserScope.PLANT_ADMIN);
        Fire f = find(fireId);
        currentUser.checkSite(scope, f.plantId());
        if ("CLEARED".equals(f.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLEARED", "이미 해제된 화재입니다.");
        }
        if (f.unlockFailCount() >= MAX_FAIL) {
            throw new ApiException(HttpStatus.LOCKED, "FIRE_UNLOCK_LOCKED", "5회 틀려 잠겼습니다. 본사 관리자 승인이 필요합니다.");
        }
        AuthRepository.User me = auth.findActiveUser(scope.userId());
        if (me == null || password == null || !passwords.matches(password, me.passwordHash())) {
            int fails = jdbc.queryForObject("""
                    UPDATE fire_incident SET unlock_fail_count = unlock_fail_count + 1 WHERE fire_id = :id
                    RETURNING unlock_fail_count
                    """, new MapSqlParameterSource("id", fireId), Integer.class);
            hub.publish("fire.updated", Map.of("fireId", fireId, "unlock", unlock(fails)),
                    s -> s.scope().isHq() || s.scope().canSite(f.plantId(), masterCache.get()));
            if (fails >= MAX_FAIL) {
                throw new ApiException(HttpStatus.LOCKED, "FIRE_UNLOCK_LOCKED", "5회 틀려 잠겼습니다. 본사 관리자 승인이 필요합니다.");
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("remaining", MAX_FAIL - fails);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "FIRE_PASSWORD_MISMATCH", "비밀번호가 틀립니다.", details);
        }
        OffsetDateTime now = time.now();
        jdbc.update("""
                UPDATE fire_incident SET status = 'CLEARED', cleared_by = :by, cleared_at = :now,
                       broadcast_ended_at = COALESCE(broadcast_ended_at, :now)
                WHERE fire_id = :id
                """, new MapSqlParameterSource("by", scope.userId()).addValue("now", now).addValue("id", fireId));
        broadcast.stop(fireId);
        pushWorkers(f.processId(), "FIRE_CLEARED", fireId);
        checklists.addAction(f.alarmId(), "MANUAL_NOTE", "화재 해제", "OK",
                memo == null || memo.isBlank() ? "화재 경보 해제" : "화재 경보 해제: " + memo, scope.userId(), now);
        try {
            central.closeAlarm(f.alarmId(), scope.userId());
        } catch (RuntimeException e) {
            log.warn("화재 해제 후 경보 종료 실패 alarm={}: {}", f.alarmId(), e.getMessage());
        }
        activity.log(scope.userId(), "FIRE_CLEAR", f.plantId(), f.processId(), "fire_incident", fireId,
                "화재 경보 해제" + (memo == null || memo.isBlank() ? "" : " (" + memo + ")"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", fireId);
        out.put("status", "CLEARED");
        out.put("clearedBy", me.name());
        out.put("clearedAt", now);
        hub.publish("fire.cleared", Map.of("fireId", fireId, "clearedBy", me.name(), "clearedAt", now),
                s -> s.scope().isHq() || s.scope().canSite(f.plantId(), masterCache.get()));
        return out;
    }

    /** POST /fires/{fireId}/approve-unlock: 본사 관리자가 잠금을 풀어 준다 */
    public Map<String, Object> approveUnlock(long fireId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN);
        Fire f = find(fireId);
        jdbc.update("UPDATE fire_incident SET unlock_fail_count = 0 WHERE fire_id = :id",
                new MapSqlParameterSource("id", fireId));
        activity.log(scope.userId(), "FIRE_CLEAR", f.plantId(), f.processId(), "fire_incident", fireId,
                "화재 해제 잠금 풀기 승인");
        hub.publish("fire.updated", Map.of("fireId", fireId, "unlock", unlock(0)),
                s -> s.scope().isHq() || s.scope().canSite(f.plantId(), masterCache.get()));
        return Map.of("id", fireId, "unlock", Map.of("failCount", 0, "locked", false));
    }

    /** POST /fires/{fireId}/evacuated: 공정 담당자 "대피했습니다" (명세서 M2) */
    public Map<String, Object> evacuated(long fireId) {
        UserScope scope = currentUser.require(UserScope.WORKER);
        Fire f = find(fireId);
        if (f.processId() == null || !scope.canProcess(f.processId(), masterCache.get())) {
            throw com.gte.argos.gateway.common.api.ApiException.outOfScope();
        }
        boolean already = false;
        for (ChecklistRepository.Action a : checklists.actions(f.alarmId())) {
            if ("EVACUATED".equals(a.action()) && a.userId() != null && a.userId() == scope.userId()) {
                already = true;
            }
        }
        if (!already) {
            checklists.addAction(f.alarmId(), "EVACUATED", "대피 완료", "OK", null, scope.userId(), time.now());
        }
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (ChecklistRepository.Action a : checklists.actions(f.alarmId())) {
            if ("EVACUATED".equals(a.action()) && a.userId() != null) {
                done.add(a.userId());
            }
        }
        int total = checklists.workers(f.processId()).size();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("evacuatedCount", done.size());
        out.put("totalCount", total);
        hub.publish("fire.updated", Map.of("fireId", fireId, "evacuated", out),
                s -> s.scope().isHq() || s.scope().canSite(f.plantId(), masterCache.get()));
        return out;
    }

    /** 그 공정 담당자들에게 화재 · 해제 푸시 (종류와 번호만) */
    private void pushWorkers(Long processId, String type, long fireId) {
        if (processId == null) {
            return;
        }
        for (ChecklistRepository.Worker w : checklists.workers(processId)) {
            push.send(checklists.fcmToken(w.id()), type, Map.of("fireId", String.valueOf(fireId)));
        }
    }

    // ------------------------------------------------------------------

    record Fire(long id, long alarmId, long plantId, Long processId, Long equipmentId, Double posX, Double posY,
                Double maxTemp, OffsetDateTime detectedAt, OffsetDateTime broadcastStartedAt,
                OffsetDateTime broadcastEndedAt, OffsetDateTime hqNotifiedAt, int unlockFailCount, String status) {
    }

    private Fire fire(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Fire(rs.getLong("fire_id"), rs.getLong("alarm_id"), rs.getLong("plant_id"),
                (Long) rs.getObject("process_id"), (Long) rs.getObject("equipment_id"),
                (Double) rs.getObject("pos_x"), (Double) rs.getObject("pos_y"), (Double) rs.getObject("max_temp"),
                zoned(rs.getObject("detected_at", OffsetDateTime.class)),
                zoned(rs.getObject("broadcast_started_at", OffsetDateTime.class)),
                zoned(rs.getObject("broadcast_ended_at", OffsetDateTime.class)),
                zoned(rs.getObject("hq_notified_at", OffsetDateTime.class)),
                rs.getInt("unlock_fail_count"), rs.getString("status"));
    }

    private Fire find(long fireId) {
        return jdbc.query("SELECT * FROM fire_incident WHERE fire_id = :id", new MapSqlParameterSource("id", fireId),
                (rs, i) -> fire(rs)).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "화재 기록이 없습니다."));
    }

    private boolean exists(long alarmId) {
        Boolean b = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM fire_incident WHERE alarm_id = :a)",
                new MapSqlParameterSource("a", alarmId), Boolean.class);
        return Boolean.TRUE.equals(b);
    }

    private static Long factoryOf(Fire f, MasterCache.Snapshot m) {
        if (f.processId() == null) {
            return null;
        }
        CentralDto.Process p = m.process(f.processId());
        return p == null ? null : p.factoryId();
    }

    /** "5공장 · 도장 공정 · 상도 건조로" (withSite 면 앞에 사업장) */
    static String location(Long factoryId, Long processId, Long equipmentId, MasterCache.Snapshot m, boolean withSite) {
        List<String> parts = new ArrayList<>();
        CentralDto.Factory fac = factoryId == null ? null : m.factory(factoryId);
        if (withSite && fac != null) {
            CentralDto.Plant pl = m.plant(fac.plantId());
            if (pl != null) {
                parts.add(pl.name());
            }
        }
        if (fac != null) {
            parts.add(fac.name());
        }
        CentralDto.Process p = processId == null ? null : m.process(processId);
        if (p != null) {
            parts.add(p.name());
        }
        CentralDto.Equipment eq = equipmentId == null ? null : m.equipment(equipmentId);
        if (eq != null) {
            parts.add(eq.name());
        }
        return String.join(" · ", parts);
    }

    private static String broadcastMessage(Long processId, MasterCache.Snapshot m) {
        CentralDto.Process p = processId == null ? null : m.process(processId);
        return (p == null ? "공장" : p.name()) + "에서 화재가 발생했습니다. 작업자는 즉시 대피하십시오.";
    }

    /** 판정 근거 한 줄: 열화상 최고 온도와 기준, 함께 오른 다른 센서 */
    static String basis(Alarm a) {
        Event ir = irEvent(a);
        StringBuilder sb = new StringBuilder();
        if (ir != null) {
            sb.append("열화상 최고 온도가 화재 기준");
            if (ir.alarm() != null) {
                sb.append("(").append(fmt(ir.alarm())).append(" ℃)");
            }
            sb.append("을 넘었습니다");
        }
        List<String> others = new ArrayList<>();
        if (a.events() != null) {
            for (Event e : a.events()) {
                if (!"IR".equals(e.type()) && e.peak() != null) {
                    others.add(com.gte.argos.gateway.central.Codes.sensorName(e.type()) + " " + fmt(e.peak())
                            + (e.unit() == null ? "" : " " + e.unit()));
                }
            }
        }
        if (!others.isEmpty()) {
            sb.append(". ").append(String.join(" · ", others)).append(" 동시 상승");
        }
        return sb.toString();
    }

    private List<Map<String, Object>> actions(Fire f) {
        List<ChecklistRepository.Action> acts = checklists.actions(f.alarmId());
        List<Map<String, Object>> out = new ArrayList<>();
        boolean active = !"CLEARED".equals(f.status());
        out.add(step("DETECTED", f.detectedAt(), true, "열화상 화재 감지", false));
        boolean broadcasting = f.broadcastStartedAt() != null && f.broadcastEndedAt() == null;
        out.add(step("BROADCAST", f.broadcastStartedAt(), f.broadcastStartedAt() != null,
                broadcasting ? "공장 경고 방송 송출 중" : f.broadcastStartedAt() == null ? "공장 경고 방송 전" : "공장 경고 방송 종료",
                broadcasting));
        ChecklistRepository.Action shut = null;
        Set<Long> evacuated = new java.util.HashSet<>();
        for (ChecklistRepository.Action ac : acts) {
            if ("SHUTDOWN".equals(ac.action()) && shut == null) {
                shut = ac;
            }
            if ("EVACUATED".equals(ac.action()) && ac.userId() != null) {
                evacuated.add(ac.userId());
            }
        }
        out.add(step("SHUTDOWN", shut == null ? null : shut.doneAt(), shut != null,
                shut == null ? "설비 셧다운 전" : ChecklistRepository.actionText(shut), false));
        int total = f.processId() == null ? 0 : checklists.workers(f.processId()).size();
        boolean allOut = total > 0 && evacuated.size() >= total;
        out.add(step("EVACUATED", null, allOut,
                "공정 담당자 대피 확인 " + evacuated.size() + "/" + total, active && !allOut));
        return out;
    }

    /** running: 지금 진행 중인 조치 ('송출 중' 칩) */
    private static Map<String, Object> step(String step, OffsetDateTime at, boolean done, String text, boolean running) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", step);
        s.put("at", at);
        s.put("done", done);
        s.put("text", text);
        s.put("running", running);
        return s;
    }

    static Map<String, Object> unlock(int fails) {
        return Map.of("failCount", fails, "maxFail", MAX_FAIL, "locked", fails >= MAX_FAIL);
    }

    private static OffsetDateTime zoned(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(SimTime.ZONE).toOffsetDateTime();
    }

    private static double num(Double d) {
        return d == null ? Double.NEGATIVE_INFINITY : d;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }
}
