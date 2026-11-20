package com.gte.argos.factory.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.common.api.ApiException;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.kafka.EdgeCommandPublisher;
import com.gte.argos.factory.kafka.EdgeMessages;
import com.gte.argos.factory.kafka.EdgeMessages.EdgeResult;
import com.gte.argos.factory.live.LiveFeed;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 설비 명령 (테이블 명세서 v5 equipment_command).
 *
 * <pre>
 * 명령   FAN_HZ   배기팬 주파수 변경 (value = 목표 Hz, 20 ~ 60. 30 이 평상시). 회전수 = Hz × 30 (30Hz = 900rpm)
 *        FAN_STOP 배기팬 정지
 *        SHUTDOWN · RESTART  설비 셧다운·재가동 (v5 에는 없지만 경보 셧다운 시연에 씀)
 * 보낸 쪽(source)  USER 사람이 화면에서 / AUTO 가스 알림 때 서버 자동 제어(FanAutoService) / RECOVERY 경보 종료 때 30Hz 복귀
 * 결과   PENDING → DONE | FAILED | EXPIRED(만료 시각 지남) | CANCELLED(셧다운으로 취소)
 *        AWAITING(수동 배기팬: 담당자 승인 대기) → 승인하면 PENDING 으로 실행 | 거절 DENIED | 알림 종료 CANCELLED
 *        DUPLICATE: 같은 설비에 같은 명령·값이 아직 PENDING 이면 실행하지 않고 기록만
 *
 * 연계 서버 → POST /internal/equipment/{id}/commands → 허용 목록·상태 검사 → equipment_command 기록 (PENDING)
 *   → 설비 응답 → result DONE, equipment.run_state·fan_rpm 반영
 *   → 피드 COMMAND_RESULT (SENT → DONE · FAILED · EXPIRED · CANCELLED · REJECTED)
 *
 * 설비 응답
 *   Kafka 켜짐(argos.kafka.enabled=true): FAN_HZ · FAN_STOP 은 엣지로 보내고(argos.edge.cmd) 엣지 결과
 *     (argos.edge.cmd.result, 약 5초 뒤)로 끝낸다 → applyEdgeResult. 15초 안에 결과가 없으면 FAILED.
 *     실제 인버터는 36 ED-FAN 하나 (다른 팬은 엣지가 REJECTED)
 *   그 밖: 시뮬레이션 (팬 1초, 셧다운·재가동 3초 뒤 완료). 셧다운·재가동은 엣지 명세에 없어서 항상 시뮬레이션
 * </pre>
 *
 * 센서값을 바꾸지는 않는다 (값 회복은 봇에 직접 명령).
 */
@Service
public class EquipmentCommandService {

    public static final String FAN_HZ = "FAN_HZ";
    public static final String FAN_STOP = "FAN_STOP";
    public static final String SHUTDOWN = "SHUTDOWN";
    public static final String RESTART = "RESTART";

    /** 허용된 명령만 받는다 */
    public static final List<String> ALLOWED = List.of(FAN_HZ, FAN_STOP, SHUTDOWN, RESTART);
    public static final List<String> SOURCES = List.of("USER", "AUTO", "RECOVERY");
    public static final int HZ_MIN = 20;
    public static final int HZ_MAX = 60;
    /** 평상시 주파수. 경보가 끝나면 여기로 되돌린다 (2026-11-20 팀 기준: 정상 30 · 주의 45 · 경보 60 Hz) */
    public static final int HZ_NORMAL = 30;
    /** 회전수 = Hz × 30 */
    public static final int RPM_PER_HZ = 30;
    /** 이 시간 안에 설비가 응답하지 않으면 만료 */
    static final Duration EXPIRES_IN = Duration.ofSeconds(30);
    /** 엣지가 이 시간 안에 결과를 보내지 않으면 FAILED (엣지는 인버터에 쓰고 5초 뒤 결과를 보낸다) */
    static final Duration EDGE_TIMEOUT = Duration.ofSeconds(15);
    /** 엣지 결과 값 (argos.edge.cmd.result result) */
    static final List<String> EDGE_RESULTS = List.of("DONE", "FAILED", "REJECTED", "EXPIRED", "DUPLICATE");

    private static final Logger log = LoggerFactory.getLogger(EquipmentCommandService.class);

    private final JdbcTemplate jdbc;
    private final DemoClock clock;
    private final LiveFeed feed;
    private final ObjectMapper json;
    /** 엣지 Kafka (꺼져 있으면 null → 시뮬레이션) */
    private EdgeCommandPublisher edge;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "equipment-command");
        t.setDaemon(true);
        return t;
    });

    public EquipmentCommandService(JdbcTemplate jdbc, DemoClock clock, LiveFeed feed, ObjectMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.feed = feed;
        this.json = json;
    }

    @Autowired(required = false)
    void setEdge(EdgeCommandPublisher edge) {
        this.edge = edge;
    }

    /** 사람이 화면에서 보낸 명령 */
    @Transactional
    public CommandResult request(long equipmentId, String command, Double value, Long alarmId, Long userId) {
        return request(equipmentId, command, value, alarmId, userId, "USER");
    }

    @Transactional
    public CommandResult request(long equipmentId, String command, Double value, Long alarmId, Long userId,
                                 String source) {
        if (command == null || !ALLOWED.contains(command)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMAND_NOT_ALLOWED", "허용되지 않은 명령입니다.");
        }
        if (!SOURCES.contains(source)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "source 는 USER · AUTO · RECOVERY 중 하나입니다.");
        }
        List<EquipmentRow> rows = jdbc.query(
                "SELECT equipment_id, code, equipment_type, run_state, fan_rpm FROM equipment WHERE equipment_id = ? AND is_active",
                (rs, i) -> new EquipmentRow(rs.getLong("equipment_id"), rs.getString("code"), rs.getString("equipment_type"),
                        rs.getString("run_state"), rs.getObject("fan_rpm", Integer.class)), equipmentId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        EquipmentRow eq = rows.get(0);
        boolean fanCommand = command.startsWith("FAN_");
        if (fanCommand && !"FAN".equals(eq.type())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_FAN", "배기팬이 아닌 설비입니다.");
        }
        if (fanCommand && !"RUN".equals(eq.runState())) {
            throw ApiException.conflict("EQUIPMENT_STOPPED", "셧다운된 설비입니다.");
        }
        if (FAN_HZ.equals(command) && (value == null || value < HZ_MIN || value > HZ_MAX)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HZ_OUT_OF_RANGE",
                    "주파수는 " + HZ_MIN + " ~ " + HZ_MAX + " Hz 사이여야 합니다.");
        }
        if (SHUTDOWN.equals(command) && !"RUN".equals(eq.runState())) {
            throw ApiException.conflict("ALREADY_STOPPED", "이미 정지된 설비입니다.");
        }
        if (RESTART.equals(command) && "RUN".equals(eq.runState())) {
            throw ApiException.conflict("ALREADY_RUNNING", "이미 가동 중인 설비입니다.");
        }
        Double v = FAN_HZ.equals(command) ? Double.valueOf(Math.round(value)) : null;

        OffsetDateTime now = clock.nowOffset();
        OffsetDateTime expiresAt = now.plus(EXPIRES_IN);
        if (isDuplicate(equipmentId, command, v, now)) {
            Long id = insert(equipmentId, alarmId, command, v, source, "DUPLICATE", userId, now, expiresAt);
            jdbc.update("UPDATE equipment_command SET completed_at = ?, result_detail = ?::jsonb WHERE command_id = ?",
                    now, detail(Map.of("message", "같은 명령이 아직 처리 중")), id);
            return new CommandResult(id, equipmentId, command, "DUPLICATE", eq.runState(), eq.fanRpm(), v, source);
        }
        Long commandId = insert(equipmentId, alarmId, command, v, source, "PENDING", userId, now, expiresAt);
        if (command.startsWith("FAN_")) {
            // 바꾸기 전 회전수 (알림 응답 fanAuto.beforeRpm)
            jdbc.update("UPDATE equipment_command SET result_detail = ?::jsonb WHERE command_id = ?",
                    detail(Map.of("beforeRpm", eq.fanRpm() == null ? 0 : eq.fanRpm())), commandId);
        }

        String runState = eq.runState();
        if (SHUTDOWN.equals(command)) {
            runState = "STOPPING";
            jdbc.update("UPDATE equipment SET run_state = 'STOPPING' WHERE equipment_id = ?", equipmentId);
            cancelPendingFanCommands(equipmentId, commandId, now);
        }
        publish(commandId, equipmentId, command, v, "SENT", runState, eq.fanRpm(), now);

        if (fanCommand && edge != null) {
            String code = eq.code();
            afterCommit(() -> scheduler.execute(() -> dispatch(commandId, equipmentId, code, command, v, source)));
        } else {
            long delayMs = SHUTDOWN.equals(command) || RESTART.equals(command) ? 3_000 : 1_000;
            afterCommit(() -> scheduler.schedule(() -> complete(commandId, equipmentId, command, v),
                    delayMs, TimeUnit.MILLISECONDS));
        }
        return new CommandResult(commandId, equipmentId, command, "SENT", runState, eq.fanRpm(), v, source);
    }

    /**
     * 경보 종료 때 배기팬을 평상시 주파수(30Hz)로 되돌린다 (source RECOVERY, 요청자 없음).
     * 대상: 이 경보로 보낸 FAN_HZ 명령(사람·자동)의 팬 + 경보 발생 뒤 같은 공정에서 사람이 FAN_HZ 를 바꾼 팬.
     * 지금 가동 중이고 30Hz 가 아닌 팬만 보낸다. 돌려준 명령 목록.
     */
    @Transactional
    public List<CommandResult> recoverFans(long alarmId) {
        List<Long> fans = jdbc.queryForList("""
                SELECT DISTINCT c.equipment_id
                FROM equipment_command c
                JOIN equipment e ON e.equipment_id = c.equipment_id
                JOIN alarm a ON a.alarm_id = ?
                JOIN control_unit cu ON cu.unit_id = a.unit_id
                WHERE c.command = 'FAN_HZ' AND c.source IN ('USER', 'AUTO') AND c.result = 'DONE'
                  AND (c.alarm_id = a.alarm_id
                       OR (c.source = 'USER' AND e.process_id = cu.process_id AND c.requested_at >= a.raised_at))
                  AND e.equipment_type = 'FAN' AND e.is_active AND e.run_state = 'RUN'
                  AND COALESCE(e.fan_rpm, 0) <> ?
                ORDER BY c.equipment_id
                """, Long.class, alarmId, HZ_NORMAL * RPM_PER_HZ);
        List<CommandResult> out = new ArrayList<>();
        for (Long fan : fans) {
            out.add(request(fan, FAN_HZ, (double) HZ_NORMAL, alarmId, null, "RECOVERY"));
        }
        if (!out.isEmpty()) {
            log.info("경보 {} 종료: 배기팬 {}대 {}Hz 로 복귀", alarmId, out.size(), HZ_NORMAL);
        }
        return out;
    }

    /**
     * 수동(fan_auto = false) 배기팬: 가스 알림 때 바로 보내지 않고 승인 대기(AWAITING)로 남긴다.
     * 같은 알림·팬에 승인 대기가 이미 있으면 목표 주파수만 올린다 (주의 → 경보). 돌려준 명령 id
     */
    @Transactional
    public long await(long fanId, double hz, long alarmId, Integer beforeRpm) {
        OffsetDateTime now = clock.nowOffset();
        List<Long> open = jdbc.queryForList("""
                SELECT command_id FROM equipment_command
                WHERE equipment_id = ? AND alarm_id = ? AND result = 'AWAITING' ORDER BY command_id DESC LIMIT 1
                """, Long.class, fanId, alarmId);
        long id;
        if (!open.isEmpty()) {
            id = open.get(0);
            jdbc.update("UPDATE equipment_command SET value = GREATEST(value, ?) WHERE command_id = ?", hz, id);
        } else {
            id = insert(fanId, alarmId, FAN_HZ, hz, "AUTO", "AWAITING", null, now, null);
            jdbc.update("UPDATE equipment_command SET result_detail = ?::jsonb WHERE command_id = ?",
                    detail(Map.of("beforeRpm", beforeRpm == null ? 0 : beforeRpm)), id);
        }
        publishCurrent(id, fanId, FAN_HZ, hz, "AWAITING", now);
        return id;
    }

    /** 승인 대기 명령 승인 → 실행 (PENDING, 1초 뒤 완료). 승인 대기가 아니면 409 COMMAND_NOT_AWAITING */
    @Transactional
    public CommandResult approve(long commandId, Long userId) {
        Map<String, Object> c = awaiting(commandId);
        long equipmentId = ((Number) c.get("equipment_id")).longValue();
        Double value = c.get("value") == null ? null : ((Number) c.get("value")).doubleValue();
        Map<String, Object> eq = jdbc.queryForMap("SELECT code, run_state FROM equipment WHERE equipment_id = ?",
                equipmentId);
        if (!"RUN".equals(eq.get("run_state"))) {
            throw ApiException.conflict("EQUIPMENT_STOPPED", "셧다운된 설비입니다.");
        }
        OffsetDateTime now = clock.nowOffset();
        jdbc.update("""
                UPDATE equipment_command SET result = 'PENDING', approved_by = ?, approved_at = ?, expires_at = ?
                WHERE command_id = ? AND result = 'AWAITING'
                """, userId, now, now.plus(EXPIRES_IN), commandId);
        publishCurrent(commandId, equipmentId, FAN_HZ, value, "SENT", now);
        if (edge != null) {
            String code = (String) eq.get("code");
            String source = (String) c.get("source");
            afterCommit(() -> scheduler.execute(() -> dispatch(commandId, equipmentId, code, FAN_HZ, value, source)));
        } else {
            afterCommit(() -> scheduler.schedule(() -> complete(commandId, equipmentId, FAN_HZ, value),
                    1_000, TimeUnit.MILLISECONDS));
        }
        return find(commandId);
    }

    /** 승인 대기 명령 거절 (DENIED). 승인 대기가 아니면 409 */
    @Transactional
    public CommandResult deny(long commandId, Long userId) {
        Map<String, Object> c = awaiting(commandId);
        long equipmentId = ((Number) c.get("equipment_id")).longValue();
        Double value = c.get("value") == null ? null : ((Number) c.get("value")).doubleValue();
        OffsetDateTime now = clock.nowOffset();
        jdbc.update("""
                UPDATE equipment_command SET result = 'DENIED', approved_by = ?, approved_at = ?, completed_at = ?,
                       result_detail = COALESCE(result_detail, '{}'::jsonb) || '{"message": "담당자가 거절"}'::jsonb
                WHERE command_id = ? AND result = 'AWAITING'
                """, userId, now, now, commandId);
        publishCurrent(commandId, equipmentId, FAN_HZ, value, "DENIED", now);
        return find(commandId);
    }

    /** 알림이 끝나면 그 알림의 승인 대기 명령은 취소 */
    @Transactional
    public void cancelAwaiting(long alarmId) {
        OffsetDateTime now = clock.nowOffset();
        for (Map<String, Object> c : jdbc.queryForList(
                "SELECT command_id, equipment_id, value FROM equipment_command WHERE alarm_id = ? AND result = 'AWAITING'",
                alarmId)) {
            long id = ((Number) c.get("command_id")).longValue();
            jdbc.update("""
                    UPDATE equipment_command SET result = 'CANCELLED', completed_at = ?,
                           result_detail = COALESCE(result_detail, '{}'::jsonb) || '{"message": "알림 종료로 취소"}'::jsonb
                    WHERE command_id = ?
                    """, now, id);
            publishCurrent(id, ((Number) c.get("equipment_id")).longValue(), FAN_HZ,
                    c.get("value") == null ? null : ((Number) c.get("value")).doubleValue(), "CANCELLED", now);
        }
    }

    private Map<String, Object> awaiting(long commandId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT equipment_id, value, result, source FROM equipment_command WHERE command_id = ?", commandId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("COMMAND_NOT_FOUND", "없는 명령입니다.");
        }
        if (!"AWAITING".equals(rows.get(0).get("result"))) {
            throw ApiException.conflict("COMMAND_NOT_AWAITING", "승인 대기 중인 명령이 아닙니다.");
        }
        return rows.get(0);
    }

    /** 명령 한 건 조회 */
    public CommandResult find(long commandId) {
        List<CommandResult> rows = jdbc.query("""
                SELECT c.command_id, c.equipment_id, c.command, c.value, c.source, c.result, e.run_state, e.fan_rpm
                FROM equipment_command c JOIN equipment e ON e.equipment_id = c.equipment_id
                WHERE c.command_id = ?
                """, (rs, i) -> new CommandResult(rs.getLong("command_id"), rs.getLong("equipment_id"),
                rs.getString("command"), status(rs.getString("result")), rs.getString("run_state"),
                rs.getObject("fan_rpm", Integer.class), rs.getObject("value", Double.class), rs.getString("source")),
                commandId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("COMMAND_NOT_FOUND", "없는 명령입니다.");
        }
        return rows.get(0);
    }

    /**
     * 엣지 명령 결과 반영. 이 서버가 보내고 아직 처리 중(PENDING)이며 설비·명령이 같은 것만 반영한다
     * (여러 서버가 같은 결과 토픽을 받으므로, 다른 DB 의 같은 번호 명령을 덮어쓰지 않게). 반영했으면 true.
     * 엣지 DUPLICATE(엣지가 최근에 받은 같은 command_id)는 실행되지 않은 것이므로 FAILED 로 남긴다
     * (우리 DUPLICATE 는 "같은 명령 처리 중"이라 화면에서 '반영됨'으로 보이기 때문).
     */
    @Transactional
    public boolean applyEdgeResult(EdgeResult r) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT equipment_id, command, value, result FROM equipment_command WHERE command_id = ?", r.commandId());
        if (rows.isEmpty()) {
            log.info("엣지 명령 결과 무시: 이 서버에 없는 명령 {}", r.commandId());
            return false;
        }
        Map<String, Object> row = rows.get(0);
        long equipmentId = ((Number) row.get("equipment_id")).longValue();
        String command = (String) row.get("command");
        if (!"PENDING".equals(row.get("result")) || equipmentId != r.equipmentId() || !command.equals(r.command())) {
            log.info("엣지 명령 결과 무시: 명령 {} (우리 {} {} {}, 엣지 {} {} {})", r.commandId(), equipmentId, command,
                    row.get("result"), r.equipmentId(), r.command(), r.result());
            return false;
        }
        if (!EDGE_RESULTS.contains(r.result())) {
            log.warn("엣지 명령 결과 무시: 모르는 result {} (명령 {})", r.result(), r.commandId());
            return false;
        }
        String result = "DUPLICATE".equals(r.result()) ? "FAILED" : r.result();
        Double value = row.get("value") == null ? null : ((Number) row.get("value")).doubleValue();
        OffsetDateTime now = clock.nowOffset();
        if (finish(r.commandId(), result, now, r.detail()) == 0) {
            return false; // 그 사이 시간 초과·셧다운 취소
        }
        if ("DONE".equals(result)) {
            int rpm = r.fanRpm() != null ? r.fanRpm()
                    : FAN_STOP.equals(command) ? 0 : (value == null ? HZ_NORMAL : value.intValue()) * RPM_PER_HZ;
            jdbc.update("UPDATE equipment SET fan_rpm = ? WHERE equipment_id = ?", rpm, equipmentId);
        }
        log.info("엣지 명령 결과: 명령 {} 설비 {} {} {} → {}", r.commandId(), equipmentId, command, value, r.result());
        publishCurrent(r.commandId(), equipmentId, command, value, result, now);
        return true;
    }

    /** 엣지로 보내고, 결과가 안 오면 FAILED 로 끝낼 시간 초과를 걸어 둔다 */
    private void dispatch(long commandId, long equipmentId, String code, String command, Double value, String source) {
        // 만료 시각은 실제 시각 기준 (엣지는 센서 데이터 ts 로 "지금"을 잡는데, 재생 스크립트가 실제 시각으로 보낸다)
        OffsetDateTime expiresAt = OffsetDateTime.now(clock.zone()).plus(EXPIRES_IN);
        try {
            edge.send(equipmentId, EdgeMessages.command(commandId, equipmentId, code, command, value, source, expiresAt))
                    .whenComplete((ok, e) -> {
                        if (e != null) {
                            edgeFailed(commandId, equipmentId, command, value, "엣지로 보내지 못함: " + e.getMessage());
                        }
                    });
            log.info("엣지로 명령 보냄: 명령 {} 설비 {}({}) {} {}", commandId, equipmentId, code, command, value);
        } catch (RuntimeException e) {
            edgeFailed(commandId, equipmentId, command, value, "엣지로 보내지 못함: " + e.getMessage());
            return;
        }
        scheduler.schedule(() -> edgeFailed(commandId, equipmentId, command, value,
                "엣지 응답 없음 (" + EDGE_TIMEOUT.toSeconds() + "초)"), EDGE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 아직 처리 중이면 FAILED 로 끝낸다 (이미 끝났으면 아무것도 안 함) */
    private void edgeFailed(long commandId, long equipmentId, String command, Double value, String message) {
        try {
            OffsetDateTime now = clock.nowOffset();
            if (finish(commandId, "FAILED", now, Map.of("message", message)) > 0) {
                log.warn("설비 명령 {} 실패: {}", commandId, message);
                publishCurrent(commandId, equipmentId, command, value, "FAILED", now);
            }
        } catch (RuntimeException e) {
            log.error("설비 명령 {} 실패 처리 중 오류", commandId, e);
        }
    }

    /** 설비 응답 (시뮬레이션) */
    private void complete(long commandId, long equipmentId, String command, Double value) {
        try {
            OffsetDateTime now = clock.nowOffset();
            String result = jdbc.queryForObject("SELECT result FROM equipment_command WHERE command_id = ?",
                    String.class, commandId);
            if (!"PENDING".equals(result)) {
                return; // 셧다운으로 취소됨 등
            }
            OffsetDateTime expiresAt = jdbc.queryForObject(
                    "SELECT expires_at FROM equipment_command WHERE command_id = ?", OffsetDateTime.class, commandId);
            if (expiresAt != null && now.isAfter(expiresAt)) {
                finish(commandId, "EXPIRED", now, Map.of("message", "만료 시각이 지나 실행하지 않음"));
                publishCurrent(commandId, equipmentId, command, value, "EXPIRED", now);
                return;
            }
            String runState = "RUN";
            Map<String, Object> detail = new LinkedHashMap<>();
            switch (command) {
                case SHUTDOWN -> {
                    runState = "STOPPED";
                    jdbc.update("UPDATE equipment SET run_state = 'STOPPED', "
                            + "fan_rpm = CASE WHEN equipment_type = 'FAN' THEN 0 ELSE fan_rpm END WHERE equipment_id = ?",
                            equipmentId);
                    detail.put("message", "설비 정지");
                }
                case RESTART -> {
                    jdbc.update("UPDATE equipment SET run_state = 'RUN', "
                            + "fan_rpm = CASE WHEN equipment_type = 'FAN' THEN ? ELSE fan_rpm END WHERE equipment_id = ?",
                            HZ_NORMAL * RPM_PER_HZ, equipmentId);
                    detail.put("message", "설비 재가동");
                }
                case FAN_STOP -> {
                    jdbc.update("UPDATE equipment SET fan_rpm = 0 WHERE equipment_id = ?", equipmentId);
                    detail.put("targetHz", 0);
                    detail.put("outputHz", 0);
                    detail.put("message", "배기팬 정지");
                }
                default -> { // FAN_HZ
                    int hz = value == null ? HZ_NORMAL : value.intValue();
                    jdbc.update("UPDATE equipment SET fan_rpm = ? WHERE equipment_id = ?", hz * RPM_PER_HZ, equipmentId);
                    detail.put("targetHz", hz);
                    detail.put("outputHz", hz);
                    detail.put("message", "목표 주파수 도달");
                }
            }
            finish(commandId, "DONE", now, detail);
            Integer rpm = jdbc.queryForObject("SELECT fan_rpm FROM equipment WHERE equipment_id = ?", Integer.class, equipmentId);
            publish(commandId, equipmentId, command, value, "DONE", runState, rpm, now);
        } catch (RuntimeException e) {
            log.error("설비 명령 {} 완료 처리 실패", commandId, e);
            OffsetDateTime now = clock.nowOffset();
            finish(commandId, "FAILED", now, Map.of("message", String.valueOf(e.getMessage())));
            publish(commandId, equipmentId, command, value, "FAILED", null, null, now);
        }
    }

    private boolean isDuplicate(long equipmentId, String command, Double value, OffsetDateTime now) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM equipment_command
                WHERE equipment_id = ? AND command = ? AND value IS NOT DISTINCT FROM ?
                  AND result = 'PENDING' AND (expires_at IS NULL OR expires_at > ?)
                """, Integer.class, equipmentId, command, value, now);
        return n != null && n > 0;
    }

    private Long insert(long equipmentId, Long alarmId, String command, Double value, String source, String result,
                        Long userId, OffsetDateTime now, OffsetDateTime expiresAt) {
        return jdbc.queryForObject("""
                INSERT INTO equipment_command
                    (equipment_id, alarm_id, command, value, source, result, requested_by, requested_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING command_id
                """, Long.class, equipmentId, alarmId, command, value, source, result, userId, now, expiresAt);
    }

    /** 셧다운하면 그 설비에 아직 처리 중인 팬 명령은 취소 */
    private void cancelPendingFanCommands(long equipmentId, long shutdownId, OffsetDateTime now) {
        List<Map<String, Object>> pending = jdbc.queryForList("""
                SELECT command_id, command, value FROM equipment_command
                WHERE equipment_id = ? AND command_id <> ? AND result = 'PENDING' AND command LIKE 'FAN\\_%'
                """, equipmentId, shutdownId);
        for (Map<String, Object> p : pending) {
            long id = ((Number) p.get("command_id")).longValue();
            finish(id, "CANCELLED", now, Map.of("message", "셧다운으로 취소"));
            Double value = p.get("value") == null ? null : ((Number) p.get("value")).doubleValue();
            publish(id, equipmentId, (String) p.get("command"), value, "CANCELLED", "STOPPING", null, now);
        }
    }

    private int finish(long commandId, String result, OffsetDateTime at, Map<String, Object> detail) {
        return jdbc.update("""
                UPDATE equipment_command SET result = ?, completed_at = ?,
                       result_detail = COALESCE(result_detail, '{}'::jsonb) || ?::jsonb
                WHERE command_id = ? AND result = 'PENDING'
                """, result, at, detail(detail), commandId);
    }

    private void publishCurrent(long commandId, long equipmentId, String command, Double value, String status,
                                OffsetDateTime at) {
        Map<String, Object> eq = jdbc.queryForMap("SELECT run_state, fan_rpm FROM equipment WHERE equipment_id = ?", equipmentId);
        publish(commandId, equipmentId, command, value, status, (String) eq.get("run_state"),
                eq.get("fan_rpm") == null ? null : ((Number) eq.get("fan_rpm")).intValue(), at);
    }

    private void publish(long commandId, long equipmentId, String command, Double value, String status,
                         String runState, Integer fanRpm, OffsetDateTime at) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("commandId", commandId);
        data.put("equipmentId", equipmentId);
        data.put("command", command);
        data.put("value", value);
        data.put("status", status);
        data.put("runState", runState);
        data.put("fanRpm", fanRpm);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT alarm_id, source, approved_by FROM equipment_command WHERE command_id = ?", commandId);
        data.put("alarmId", row.get("alarm_id"));
        data.put("source", row.get("source"));
        // 수동 승인으로 실행한 명령 (연계 서버가 '자동' 이용 로그를 남기지 않게)
        data.put("approvedBy", row.get("approved_by"));
        feed.append("COMMAND_RESULT", at, data);
    }

    private String detail(Map<String, Object> detail) {
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /** 트랜잭션이 끝난 뒤 실행 (완료 처리가 아직 커밋 안 된 명령 행을 못 읽는 일 방지) */
    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    /** DB 결과 → API 상태. PENDING 은 SENT, 나머지는 그대로 (AWAITING · DENIED 포함) */
    static String status(String result) {
        return "PENDING".equals(result) ? "SENT" : result;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private record EquipmentRow(long id, String code, String type, String runState, Integer fanRpm) {
    }

    /**
     * @param status  SENT | DONE | FAILED | EXPIRED | CANCELLED | DUPLICATE | REJECTED(엣지: 인버터 없음 등)
     * @param value   FAN_HZ 의 목표 주파수 (그 밖은 null)
     * @param source  USER | AUTO | RECOVERY
     */
    public record CommandResult(long commandId, long equipmentId, String command, String status, String runState,
                                Integer fanRpm, Double value, String source) {
    }
}
