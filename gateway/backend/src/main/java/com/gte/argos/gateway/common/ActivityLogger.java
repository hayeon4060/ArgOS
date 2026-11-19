package com.gte.argos.gateway.common;

import com.gte.argos.gateway.central.SimTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 이용 로그(activity_log) 남기기. 셧다운, 팬 조작, 체크리스트 담당자 변경 등 사람이 한 일.
 * 테이블은 로그인 쪽 V1 마이그레이션에 있다. 시각은 시연 시계.
 */
@Component
public class ActivityLogger {

    private final JdbcTemplate jdbc;
    private final SimTime time;

    public ActivityLogger(JdbcTemplate jdbc, SimTime time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    /**
     * @param type        SHUTDOWN · FAN · CHECKLIST · USER … (이용 로그 화면의 유형)
     * @param targetTable 바뀐 대상 테이블
     * @param targetId    바뀐 대상 id
     * @param summary     화면 "내용" 열의 한 줄
     */
    public void log(long userId, String type, Long plantId, Long processId, String targetTable, Long targetId,
                    String summary) {
        logAs((Long) userId, type, plantId, processId, targetTable, targetId, summary);
    }

    /** 사람이 아닌 서버가 한 일 (배기팬 자동 제어 등). userId 가 null 이면 '시스템' */
    public void logAs(Long userId, String type, Long plantId, Long processId, String targetTable, Long targetId,
                      String summary) {
        jdbc.update("""
                INSERT INTO activity_log (user_id, plant_id, process_id, type, target_table, target_id, summary, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, plantId, processId, type, targetTable, targetId, summary, time.now());
    }
}
