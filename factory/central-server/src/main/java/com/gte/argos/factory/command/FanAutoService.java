package com.gte.argos.factory.command;

import com.gte.argos.factory.common.api.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 배기팬 자동 제어 (gateway/frontend/docs/fan-auto-api.md).
 *
 * <pre>
 * 가스(GAS) 알림이 주의(2)로 생기면 45Hz(75%, 1350rpm), 경보(3)로 생기거나 올라가면 60Hz(100%, 1800rpm)
 * - 대상: 그 관제 단위 센서가 달린 배기팬 (equipment_type = 'FAN')
 * - equipment.fan_auto = true 인 팬은 바로 명령. false(수동)면 승인 대기(AWAITING) 명령만 남김
 *   → 담당자가 승인하면 실행, 거절하면 DENIED (docs/api/erd-final-frontend-check.md 4장 1번)
 * - 명령은 equipment_command 에 source = AUTO, alarm_id 를 채워 남긴다. 요청자 없음
 * - 화재(FIRE)는 대상 아님 (화재는 셧다운)
 * - 실시간에서만 부른다 (7일치 가져오기는 명령을 보내지 않음)
 * </pre>
 */
@Service
public class FanAutoService {

    public static final int HZ_WARN = 45;
    public static final int HZ_ALARM = 60;

    private static final Logger log = LoggerFactory.getLogger(FanAutoService.class);

    private final JdbcTemplate jdbc;
    private final EquipmentCommandService commands;

    /**
     * 값이 정상으로 돌아와 경보가 자동으로 닫힘 (2026-11-20): 사람이 닫을 때와 같이
     * 승인 대기 배기팬 명령을 취소하고, 경보 중 올린 배기팬을 평상시(30Hz)로 되돌린다
     */
    public void onAlarmClosed(long alarmId) {
        commands.cancelAwaiting(alarmId);
        commands.recoverFans(alarmId);
    }

    public FanAutoService(JdbcTemplate jdbc, EquipmentCommandService commands) {
        this.jdbc = jdbc;
        this.commands = commands;
    }

    /** 단계별 목표 주파수. 주의 45, 경보 60, 그 밖은 null */
    public static Integer targetHz(int stage) {
        return switch (stage) {
            case 2 -> HZ_WARN;
            case 3 -> HZ_ALARM;
            default -> null;
        };
    }

    /** 목표 주파수의 % (최고 60Hz = 100%) */
    public static int percent(int hz) {
        return Math.round(hz * 100f / EquipmentCommandService.HZ_MAX);
    }

    /**
     * 가스 알림이 생기거나 단계가 올라갔을 때. 보낸 명령 목록.
     * 이미 그 회전수 이상으로 도는 팬은 보내지 않는다 (사람이 더 올려 둔 값을 낮추지 않음).
     */
    public List<EquipmentCommandService.CommandResult> onGasAlarm(long alarmId, long unitId, int stage) {
        Integer hz = targetHz(stage);
        if (hz == null) {
            return List.of();
        }
        int targetRpm = hz * EquipmentCommandService.RPM_PER_HZ;
        List<EquipmentCommandService.CommandResult> out = new ArrayList<>();
        for (Map<String, Object> fan : fans(unitId)) {
            long fanId = ((Number) fan.get("equipment_id")).longValue();
            Number rpm = (Number) fan.get("fan_rpm");
            if (rpm != null && rpm.intValue() >= targetRpm) {
                continue;
            }
            if (!Boolean.TRUE.equals(fan.get("fan_auto"))) {
                long id = commands.await(fanId, hz, alarmId, rpm == null ? null : rpm.intValue());
                log.info("가스 알림 {} ({}단계): 수동 배기팬 {} {}Hz 승인 대기 (명령 {})", alarmId, stage, fanId, hz, id);
                continue;
            }
            try {
                out.add(commands.request(fanId, EquipmentCommandService.FAN_HZ, (double) hz, alarmId, null, "AUTO"));
                log.info("가스 알림 {} ({}단계): 배기팬 {} 자동 {}Hz", alarmId, stage, fanId, hz);
            } catch (ApiException e) {
                log.warn("가스 알림 {}: 배기팬 {} 자동 제어 못 함 ({})", alarmId, fanId, e.getMessage());
            }
        }
        return out;
    }

    /** 관제 단위 센서가 달린 배기팬 */
    List<Map<String, Object>> fans(long unitId) {
        return jdbc.queryForList("""
                SELECT DISTINCT e.equipment_id, e.fan_auto, e.fan_rpm
                FROM control_unit_sensor cus
                JOIN sensor s ON s.sensor_id = cus.sensor_id
                JOIN equipment e ON e.equipment_id = s.equipment_id
                WHERE cus.unit_id = ? AND e.equipment_type = 'FAN' AND e.is_active AND e.run_state = 'RUN'
                ORDER BY e.equipment_id
                """, unitId);
    }
}
