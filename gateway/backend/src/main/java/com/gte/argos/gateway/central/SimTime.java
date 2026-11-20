package com.gte.argos.gateway.central;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 연계 서버의 "지금". 시연 시계(그날은 2026-11-16 09:00)는 중앙 서버가 갖고 있으므로 거기에 맞춘다.
 * 중앙 시계를 5초마다 다시 읽고, 그 사이는 실제로 흐른 시간만큼 더한다.
 * 중앙에 연결이 안 되면 실제 시각을 쓴다.
 */
@Component
public class SimTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration REFRESH = Duration.ofSeconds(5);
    private static final Logger log = LoggerFactory.getLogger(SimTime.class);

    private final CentralClient central;
    private volatile Instant centralNow;
    private volatile Instant fetchedAt = Instant.EPOCH;

    public SimTime(CentralClient central) {
        this.central = central;
    }

    public OffsetDateTime now() {
        Instant real = Instant.now();
        if (centralNow == null || real.isAfter(fetchedAt.plus(REFRESH))) {
            try {
                centralNow = central.clock().now().toInstant();
                fetchedAt = Instant.now();
                real = fetchedAt;
            } catch (RuntimeException e) {
                if (centralNow == null) {
                    log.debug("중앙 시계를 읽지 못해 실제 시각을 씁니다: {}", e.getMessage());
                    return real.atZone(ZONE).toOffsetDateTime();
                }
            }
        }
        return centralNow.plus(Duration.between(fetchedAt, real)).atZone(ZONE).toOffsetDateTime();
    }

    /** 리셋 직후처럼 바로 다시 읽어야 할 때 */
    public void invalidate() {
        fetchedAt = Instant.EPOCH;
    }

    /** 오늘 0시 */
    public OffsetDateTime startOfToday() {
        return now().atZoneSameInstant(ZONE).truncatedTo(ChronoUnit.DAYS).toOffsetDateTime();
    }

    /**
     * date 파라미터 해석: null 또는 "today" = 오늘, "2026-11-12" = 그날.
     *
     * @return [시작, 끝) — 오늘이면 끝은 지금 + 1초
     */
    public OffsetDateTime[] dayRange(String date) {
        if (date == null || date.isBlank() || "today".equalsIgnoreCase(date)) {
            return new OffsetDateTime[] {startOfToday(), now().plusSeconds(1)};
        }
        OffsetDateTime start = java.time.LocalDate.parse(date).atStartOfDay(ZONE).toOffsetDateTime();
        return new OffsetDateTime[] {start, start.plusDays(1)};
    }
}
