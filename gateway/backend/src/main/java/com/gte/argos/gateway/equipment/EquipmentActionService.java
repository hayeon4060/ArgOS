package com.gte.argos.gateway.equipment;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.alarm.AlarmService;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralDto.Alarm;
import com.gte.argos.gateway.central.CentralDto.CommandResult;
import com.gte.argos.gateway.central.CentralDto.Equipment;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.checklist.ChecklistRepository;
import com.gte.argos.gateway.checklist.ChecklistRepository.Worker;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.push.PushSender;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 설비 조작 (API 명세서 B3-3 배기팬, C4-2·3 셧다운, C4-4 공정 담당자 호출).
 * 명령은 중앙 서버로 넘기고 바로 202 를 돌려준다. 결과는 SSE command.result.
 * 공장 담당자만 할 수 있다 (본사 관리자는 열람만).
 */
@Service
public class EquipmentActionService {

    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final AlarmService alarms;
    private final ChecklistRepository repo;
    private final PushSender push;
    private final ActivityLogger activity;
    private final SimTime time;

    public EquipmentActionService(CentralClient central, MasterCache masterCache, CurrentUser currentUser,
                                  AlarmService alarms, ChecklistRepository repo, PushSender push,
                                  ActivityLogger activity, SimTime time) {
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.alarms = alarms;
        this.repo = repo;
        this.push = push;
        this.activity = activity;
        this.time = time;
    }

    /** 배기팬 회전수 범위 (화면 슬라이더) */
    static final int RPM_MIN = 600;
    static final int RPM_MAX = 1800;
    /** 회전수 = 주파수 × 30 (중앙 서버와 같음. 평상시 30Hz = 900rpm, 주의 45Hz, 경보 60Hz) */
    static final int RPM_PER_HZ = 30;
    static final int HZ_NORMAL = 30;

    /**
     * POST /fan-commands/{commandId}/approve · deny — 수동 배기팬 승인 대기 명령을 공장 담당자가 승인 · 거절.
     * 승인하면 그 주파수로 실행, 거절하면 DENIED. 본사 관리자는 열람만 (403). 승인 대기가 아니면 409 COMMAND_NOT_AWAITING
     */
    public Map<String, Object> decideFan(long commandId, boolean approve) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        CommandResult before = central.findCommand(commandId);
        Equipment eq = masterCache.get().equipment(before.equipmentId());
        if (eq == null) {
            throw ApiException.notFound("COMMAND_NOT_FOUND", "없는 명령입니다.");
        }
        currentUser.checkProcess(scope, eq.processId());
        CommandResult r = central.decideCommand(commandId, approve ? "approve" : "deny", scope.userId());
        int hz = r.value() == null ? 0 : r.value().intValue();
        activity.log(scope.userId(), "FAN", plantOf(eq), eq.processId(), "equipment_command", commandId,
                eq.name() + " " + hz + "Hz · " + (hz * 30) + "rpm " + (approve ? "승인 (가스 알림 수동)" : "거절 (가스 알림 수동)"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commandId", r.commandId());
        out.put("equipmentId", r.equipmentId());
        out.put("status", r.status());
        out.put("hz", hz);
        out.put("rpm", hz * 30);
        return out;
    }

    /**
     * POST /equipment/{id}/fan — { power: ON|OFF } 또는 { rpm: 600~1800 } 또는 { hz: 20~60 }.
     * 중앙 명령(테이블 명세서 v5): 끄기 = FAN_STOP, 켜기 = FAN_HZ 40, 회전수·주파수 = FAN_HZ (rpm ÷ 30)
     */
    public Map<String, Object> fan(long equipmentId, String power, Integer rpm, Integer hz) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        Equipment eq = equipment(scope, equipmentId);
        FanCommand c = fanCommand(power, rpm, hz);
        CommandResult r = central.command(equipmentId, c.command(), c.hz() == null ? null : c.hz().doubleValue(),
                null, scope.userId());
        if ("DUPLICATE".equals(r.status())) {
            throw ApiException.conflict("COMMAND_PENDING", "같은 명령을 처리하고 있습니다. 잠시 뒤 다시 시도해 주세요.");
        }
        activity.log(scope.userId(), "FAN", plantOf(eq), eq.processId(), "equipment", equipmentId, eq.name() + " " + c.text());
        return accepted(r);
    }

    /** 화면 요청 → 중앙 명령. 회전수는 가장 가까운 주파수로 바꾼다 */
    static FanCommand fanCommand(String power, Integer rpm, Integer hz) {
        if (hz != null) {
            if (hz < RPM_MIN / RPM_PER_HZ || hz > RPM_MAX / RPM_PER_HZ) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "HZ_OUT_OF_RANGE", "주파수는 20 ~ 60 Hz 사이여야 합니다.");
            }
            return new FanCommand("FAN_HZ", hz, hz + "Hz (" + hz * RPM_PER_HZ + "rpm)");
        }
        if (rpm != null) {
            if (rpm < RPM_MIN || rpm > RPM_MAX) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "RPM_OUT_OF_RANGE", "RPM 은 600 ~ 1,800 사이여야 합니다.");
            }
            int h = Math.round(rpm / (float) RPM_PER_HZ);
            return new FanCommand("FAN_HZ", h, "RPM " + rpm + " (" + h + "Hz)");
        }
        if ("OFF".equals(power)) {
            return new FanCommand("FAN_STOP", null, "끄기");
        }
        if ("ON".equals(power)) {
            return new FanCommand("FAN_HZ", HZ_NORMAL, "켜기 (" + HZ_NORMAL + "Hz)");
        }
        throw new IllegalArgumentException("power, rpm, hz 중 하나가 필요합니다");
    }

    record FanCommand(String command, Integer hz, String text) {
    }

    /** POST /equipment/{id}/shutdown — { alarmId, reason } */
    public Map<String, Object> shutdown(long equipmentId, Long alarmId, String reason) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        Equipment eq = equipment(scope, equipmentId);
        return shutdownOne(scope, eq, alarmId, reason);
    }

    /** POST /processes/{id}/shutdown — { alarmId, equipmentIds(비우면 공정 전체), reason } */
    public Map<String, Object> shutdownProcess(long processId, Long alarmId, List<Long> equipmentIds, String reason) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        currentUser.checkProcess(scope, processId);
        MasterCache.Snapshot m = masterCache.get();
        List<Equipment> targets = new ArrayList<>();
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            for (Equipment e : m.equipmentOf(processId)) {
                if (!"CAMERA".equals(e.type()) && "RUN".equals(e.runState())) {
                    targets.add(e);
                }
            }
        } else {
            for (Long id : equipmentIds) {
                Equipment e = m.equipment(id);
                if (e == null || e.processId() != processId) {
                    throw ApiException.notFound("EQUIPMENT_NOT_FOUND", "이 공정의 설비가 아닙니다: " + id);
                }
                targets.add(e);
            }
        }
        List<Map<String, Object>> commands = new ArrayList<>();
        for (Equipment e : targets) {
            try {
                Map<String, Object> r = shutdownOne(scope, e, alarmId, reason);
                commands.add(Map.of("commandId", r.get("commandId"), "equipmentId", e.id()));
            } catch (ApiException ex) {
                if (!"ALREADY_STOPPED".equals(ex.code())) {
                    throw ex;
                }
            }
        }
        return Map.of("commands", commands);
    }

    /** POST /processes/{id}/call-workers — { alarmId } */
    public Map<String, Object> callWorkers(long processId, Long alarmId) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        currentUser.checkProcess(scope, processId);
        int sent = 0;
        int failed = 0;
        for (Worker w : repo.workers(processId)) {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("type", "WORKER_CALL");
            data.put("processId", String.valueOf(processId));
            if (alarmId != null) {
                data.put("alarmId", String.valueOf(alarmId));
            }
            if (push.send(repo.fcmToken(w.id()), "WORKER_CALL", data)) {
                sent++;
            } else {
                failed++;
            }
        }
        if (alarmId != null) {
            repo.addAction(alarmId, "CALL", sent + "명", failed == 0 ? "OK" : "FAIL", null, scope.userId(), time.now());
        }
        return Map.of("sentCount", sent, "failedCount", failed);
    }

    // ---------------------------------------------------------------

    private Map<String, Object> shutdownOne(UserScope scope, Equipment eq, Long alarmId, String reason) {
        if (alarmId != null) {
            Alarm a = alarms.visibleAlarm(alarmId);
            if (a.processId() != eq.processId()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ALARM_MISMATCH", "다른 공정의 알림입니다.");
            }
        }
        CommandResult r = central.command(eq.id(), "SHUTDOWN", null, alarmId, scope.userId());
        if (alarmId != null) {
            repo.addAction(alarmId, "SHUTDOWN", eq.name(), "PENDING", reason, scope.userId(), time.now());
            try {
                central.actingAlarm(alarmId, scope.userId());
            } catch (ApiException ignored) {
                // 이미 종료된 경보면 상태는 그대로
            }
        }
        activity.log(scope.userId(), "SHUTDOWN", plantOf(eq), eq.processId(), "equipment", eq.id(),
                eq.name() + " 셧다운" + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
        return accepted(r);
    }

    private Equipment equipment(UserScope scope, long equipmentId) {
        Equipment eq = masterCache.get().equipment(equipmentId);
        if (eq == null) {
            throw ApiException.notFound("EQUIPMENT_NOT_FOUND", "없는 설비입니다.");
        }
        currentUser.checkProcess(scope, eq.processId());
        return eq;
    }

    private Long plantOf(Equipment eq) {
        MasterCache.Snapshot m = masterCache.get();
        Long f = m.factoryOfProcess(eq.processId());
        return f == null ? null : m.plantOfFactory(f);
    }

    private static Map<String, Object> accepted(CommandResult r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("commandId", r.commandId());
        out.put("equipmentId", r.equipmentId());
        out.put("status", r.status());
        return out;
    }
}
