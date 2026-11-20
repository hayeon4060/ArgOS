package com.gte.argos.factory.retention;

import com.gte.argos.factory.common.time.DemoClock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 센서 일 집계 (sensor_daily, 테이블 명세서 v5).
 *
 * <pre>
 * - 1분 추이(sensor_trend)를 하루 단위로 묶는다: 평균(샘플 수 가중), 최소, 최대, 샘플 수, 통신 끊김 수
 * - 1분 추이는 7일 보관 뒤 지워지지만 일 집계는 남는다. 그래서 지우기 전에 먼저 집계한다 (RetentionService)
 * - 날짜는 서버 시계의 시간대(Asia/Seoul) 기준
 * - 10분마다 어제·오늘(서버 시계)을 다시 집계한다. 같은 날을 여러 번 집계해도 결과는 같다 (덮어쓰기)
 * </pre>
 */
@Service
public class SensorDailyService {

    private static final Logger log = LoggerFactory.getLogger(SensorDailyService.class);

    private final JdbcTemplate jdbc;
    private final DemoClock clock;

    public SensorDailyService(JdbcTemplate jdbc, DemoClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT10M")
    public void scheduled() {
        try {
            LocalDate today = today();
            aggregate(today.minusDays(1), today);
        } catch (RuntimeException e) {
            log.warn("센서 일 집계 실패: {}", e.getMessage());
        }
    }

    /** from ~ to (둘 다 포함) 날짜를 1분 추이로 다시 집계한다. 넣거나 고친 행 수를 돌려준다 */
    @Transactional
    public int aggregate(LocalDate from, LocalDate to) {
        String zone = clock.zone().getId();
        OffsetDateTime start = from.atStartOfDay(clock.zone()).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(clock.zone()).toOffsetDateTime();
        return jdbc.update("""
                INSERT INTO sensor_daily (sensor_id, day, avg_v, min_v, max_v, n, lost_n)
                SELECT sensor_id, day,
                       ROUND((SUM(avg_v * n) / NULLIF(SUM(n) FILTER (WHERE avg_v IS NOT NULL), 0))::numeric, 3)::double precision,
                       MIN(min_v), MAX(max_v), SUM(n), SUM(lost_n)
                FROM (
                    SELECT sensor_id, (ts AT TIME ZONE ?)::date AS day, avg_v, min_v, max_v, n, lost_n
                    FROM sensor_trend
                    WHERE ts >= ? AND ts < ?
                ) t
                GROUP BY sensor_id, day
                ON CONFLICT (sensor_id, day) DO UPDATE
                   SET avg_v = EXCLUDED.avg_v, min_v = EXCLUDED.min_v, max_v = EXCLUDED.max_v,
                       n = EXCLUDED.n, lost_n = EXCLUDED.lost_n
                """, zone, start, end);
    }

    /** 1분 추이가 남아 있는 모든 날짜를 집계한다 (7일치 가져오기 뒤, 지우기 전) */
    @Transactional
    public int aggregateAll() {
        String zone = clock.zone().getId();
        LocalDate[] range = jdbc.query("""
                SELECT MIN((ts AT TIME ZONE ?)::date) AS d0, MAX((ts AT TIME ZONE ?)::date) AS d1 FROM sensor_trend
                """, rs -> {
            rs.next();
            java.sql.Date d0 = rs.getDate("d0");
            java.sql.Date d1 = rs.getDate("d1");
            return d0 == null ? null : new LocalDate[] {d0.toLocalDate(), d1.toLocalDate()};
        }, zone, zone);
        return range == null ? 0 : aggregate(range[0], range[1]);
    }

    /**
     * 시연 리셋: 시연 시작일 이후 일 집계를 지우고, 시작일은 남은 1분 추이(09:00 이전)로 다시 만든다.
     */
    @Transactional
    public void resetFromSimStart() {
        LocalDate startDay = clock.simStart().atZone(clock.zone()).toLocalDate();
        jdbc.update("DELETE FROM sensor_daily WHERE day >= ?", startDay);
        aggregate(startDay, startDay);
    }

    private LocalDate today() {
        return clock.now().atZone(clock.zone()).toLocalDate();
    }
}
