package com.gte.argos.factory.retention;

import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.config.ArgosProperties;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 센서 값 보관 기간 관리.
 *
 * <pre>
 * - 1시간마다 (시작 1분 뒤 처음) 서버 시계 기준 (오늘 0시 - N일, 기본 31일)보다 오래된 값을 지운다
 *   · sensor_trend         : 그래프용 1분 추이 (정상값 포함 전부)
 *   · sensor_event_sample  : 경보 구간 원본 샘플
 *   · alarm_sensor_sample  : 알림 동안 관제 단위 센서 5초 값
 * - 지우지 않는 것: alarm, sensor_event (통계·이력·월간 보고서), file_asset (경보 시작 이미지), sensor_daily (일 집계)
 * - 지우기 전에 지울 날짜의 일 집계(sensor_daily)를 먼저 만든다
 * - (2026-11-20 부터 실제 서버 시각 기준) 사전 적재 30일치 1분 추이도 31일이 지나면 앞쪽부터 지워진다. 일 집계(sensor_daily)는 남는다
 * </pre>
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final JdbcTemplate jdbc;
    private final DemoClock clock;
    private final ArgosProperties.Retention retention;
    private final SensorDailyService daily;

    public RetentionService(JdbcTemplate jdbc, DemoClock clock, ArgosProperties props, SensorDailyService daily) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.daily = daily;
        this.retention = props.retention();
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void scheduled() {
        if (!retention.enabled()) {
            return;
        }
        try {
            purge();
        } catch (RuntimeException e) {
            log.warn("오래된 센서 값을 지우지 못했습니다: {}", e.getMessage());
        }
    }

    /** 지금 바로 지운다. 지운 행 수를 돌려준다 */
    @Transactional
    public Result purge() {
        OffsetDateTime cutoff = cutoff(clock.now(), retention.effectiveDays(), clock.zone()).atZone(clock.zone()).toOffsetDateTime();
        java.sql.Date oldest = jdbc.queryForObject("SELECT MIN((ts AT TIME ZONE ?)::date) FROM sensor_trend WHERE ts < ?",
                java.sql.Date.class, clock.zone().getId(), cutoff);
        if (oldest != null) {
            daily.aggregate(oldest.toLocalDate(), cutoff.toLocalDate().minusDays(1));
        }
        int trend = jdbc.update("DELETE FROM sensor_trend WHERE ts < ?", cutoff);
        int samples = jdbc.update("DELETE FROM sensor_event_sample WHERE ts < ?", cutoff);
        samples += jdbc.update("DELETE FROM alarm_sensor_sample WHERE ts < ?", cutoff);
        if (trend > 0 || samples > 0) {
            log.info("보관 기간({}일) 지난 센서 값 삭제: 1분 추이 {}행, 경보 샘플 {}행 ({} 이전)",
                    retention.effectiveDays(), trend, samples, cutoff);
        }
        return new Result(cutoff, trend, samples);
    }

    /**
     * 이 시각보다 오래된 값을 지운다 = (오늘 0시 - days일).
     * 하루 단위로 자르는 이유: 초 단위(now - 7일)로 자르면 시연 중 7일치 데이터 앞쪽이 계속 깎이고,
     * 시연 리셋으로 시계를 되돌려도 지워진 값은 돌아오지 않는다.
     */
    static Instant cutoff(Instant now, int days, ZoneId zone) {
        if (days < 1) {
            throw new IllegalArgumentException("보관 기간은 1일 이상이어야 합니다");
        }
        return now.atZone(zone).toLocalDate().minusDays(days).atStartOfDay(zone).toInstant();
    }

    /**
     * @param cutoff  이 시각 이전 값을 지움
     * @param trend   지운 1분 추이 행 수
     * @param samples 지운 경보 샘플 행 수
     */
    public record Result(OffsetDateTime cutoff, int trend, int samples) {
    }
}
