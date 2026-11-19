package com.gte.argos.factory.query;

import com.gte.argos.factory.command.EquipmentCommandService;
import com.gte.argos.factory.common.api.ApiException;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.ingest.LiveJudgementService;
import com.gte.argos.factory.live.LiveFeed;
import com.gte.argos.factory.query.InternalDto.Alarm;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경보 상태 변경. 연계 서버가 사람의 조치(확인 · 조치 중 · 종료)를 알려 주면 중앙 DB 의 alarm 에 남긴다.
 *
 * <pre>
 * OPEN → ACKED → ACTING → CLOSED
 * - 종료는 경보에 묶인 이벤트가 모두 끝났을 때만 된다 (값이 기준 아래로 30초 유지). 아니면 409 VALUE_STILL_OVER
 * - 종료하면 판정 엔진에도 알려, 그 관제 단위의 다음 이상은 새 경보가 된다
 * - 종료 사유(close_reason): 조치 중(ACTING)에 닫으면 ACTION_DONE, 아니면 MANUAL
 * - 종료하면 경보 중 주파수를 바꾼 배기팬을 40Hz 로 되돌린다 (명령 trigger RECOVERY)
 * </pre>
 */
@Service
public class AlarmStatusService {

    private final JdbcTemplate jdbc;
    private final InternalQueryRepository repo;
    private final LiveJudgementService live;
    private final LiveFeed feed;
    private final DemoClock clock;
    private final EquipmentCommandService commands;

    public AlarmStatusService(JdbcTemplate jdbc, InternalQueryRepository repo, LiveJudgementService live,
                              LiveFeed feed, DemoClock clock, EquipmentCommandService commands) {
        this.jdbc = jdbc;
        this.commands = commands;
        this.repo = repo;
        this.live = live;
        this.feed = feed;
        this.clock = clock;
    }

    /** 주의·경보 확인 (OPEN → ACKED) */
    @Transactional
    public Alarm ack(long alarmId, Long userId) {
        Alarm alarm = find(alarmId);
        if (!"OPEN".equals(alarm.status())) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("status", alarm.status());
            details.put("ackedBy", alarm.ackedBy());
            throw new ApiException(HttpStatus.CONFLICT, "ALARM_ALREADY_ACKED", "이미 확인된 알림입니다.", details);
        }
        OffsetDateTime now = clock.nowOffset();
        jdbc.update("UPDATE alarm SET status = 'ACKED', acked_by = ?, acked_at = ? WHERE alarm_id = ?",
                userId, now, alarmId);
        return changed(alarmId, "ACKED", userId, now);
    }

    /** 조치 시작 (체크리스트 전송, 셧다운 등) — OPEN · ACKED → ACTING */
    @Transactional
    public Alarm acting(long alarmId, Long userId) {
        Alarm alarm = find(alarmId);
        if ("CLOSED".equals(alarm.status())) {
            throw ApiException.conflict("ALARM_CLOSED", "이미 종료된 알림입니다.");
        }
        if ("ACTING".equals(alarm.status())) {
            return alarm;
        }
        OffsetDateTime now = clock.nowOffset();
        if ("OPEN".equals(alarm.status())) {
            // 확인 없이 바로 조치하면 확인자도 같이 남긴다
            jdbc.update("UPDATE alarm SET status = 'ACTING', acked_by = ?, acked_at = ? WHERE alarm_id = ?",
                    userId, now, alarmId);
        } else {
            jdbc.update("UPDATE alarm SET status = 'ACTING' WHERE alarm_id = ?", alarmId);
        }
        return changed(alarmId, "ACTING", userId, now);
    }

    /** 경보 종료 (→ CLOSED). 진행 중인 이벤트가 있으면 409 */
    @Transactional
    public Alarm close(long alarmId, Long userId) {
        Alarm alarm = find(alarmId);
        if ("CLOSED".equals(alarm.status())) {
            return alarm;
        }
        if (alarm.valueStillOver()) {
            List<Map<String, Object>> sensors = alarm.events().stream()
                    .filter(e -> e.endedAt() == null)
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("sensorId", e.sensorId());
                        m.put("tag", e.tag());
                        m.put("stage", e.stage());
                        return m;
                    })
                    .toList();
            throw new ApiException(HttpStatus.CONFLICT, "VALUE_STILL_OVER",
                    "아직 기준을 넘은 센서가 있어 종료할 수 없습니다.", Map.of("sensors", sensors));
        }
        OffsetDateTime now = clock.nowOffset();
        jdbc.update("UPDATE alarm SET status = 'CLOSED', closed_by = ?, closed_at = ?, close_reason = ? WHERE alarm_id = ?",
                userId, now, closeReason(alarm.status()), alarmId);
        live.closeAlarm(alarm.unitId());
        commands.recoverFans(alarmId);
        return changed(alarmId, "CLOSED", userId, now);
    }

    /**
     * 종료 사유 (alarm.close_reason). 사람이 닫는 경우만 여기로 온다.
     * 조치(체크리스트·셧다운 등)에 들어간 뒤 닫으면 ACTION_DONE, 조치 없이 닫으면 MANUAL.
     * AUTO_RECOVERED 는 과거 데이터에서 값이 스스로 돌아와 닫힌 경보.
     */
    static String closeReason(String status) {
        return "ACTING".equals(status) ? "ACTION_DONE" : "MANUAL";
    }

    private Alarm find(long alarmId) {
        Alarm alarm = repo.alarm(alarmId);
        if (alarm == null) {
            throw ApiException.notFound("ALARM_NOT_FOUND", "없는 알림입니다.");
        }
        return alarm;
    }

    private Alarm changed(long alarmId, String status, Long userId, OffsetDateTime at) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alarmId", alarmId);
        data.put("status", status);
        data.put("by", userId);
        feed.append("ALARM_STATUS", at, data);
        return repo.alarm(alarmId);
    }
}
