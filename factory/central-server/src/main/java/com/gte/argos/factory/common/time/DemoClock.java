package com.gte.argos.factory.common.time;

import com.gte.argos.factory.config.ArgosProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 서버 시계. 업무 시각(이벤트, 경보, 명령, 수집 값)은 모두 이 시계의 지금(실제 서버 시각)을 쓴다.
 * (2026-11-20 결정: 시연 시계는 쓰지 않는다. 봇·VMS·엣지가 보낸 ts 도 쓰지 않고 받은 순간의 서버 시각으로 기록)
 *
 * simStart 는 "7일치 더미 데이터가 끝나는 시각"(기본 2026-11-16 09:00)으로만 남는다.
 * 더미 가져오기·실시간 리셋이 이 시각을 경계로 쓴다. 그 뒤부터 지금까지는 실시간 데이터 몫이다.
 */
@Component
public class DemoClock {

    private final Clock realClock;
    private final ZoneId zone;
    private volatile Instant simStart;

    @Autowired
    public DemoClock(ArgosProperties props) {
        this(props.clock().simStart().toInstant(), props.zone(), Clock.systemUTC());
    }

    DemoClock(Instant simStart, ZoneId zone, Clock realClock) {
        this.realClock = realClock;
        this.zone = zone;
        this.simStart = simStart;
    }

    /** 지금 (실제 서버 시각) */
    public Instant now() {
        return realClock.instant();
    }

    /** 지금 (시간대 포함) */
    public OffsetDateTime nowOffset() {
        return now().atZone(zone).toOffsetDateTime();
    }

    /** 7일치 더미 데이터가 끝나는 시각 (이보다 앞은 더미, 뒤는 실시간) */
    public Instant simStart() {
        return simStart;
    }

    public ZoneId zone() {
        return zone;
    }

    /** 예전 시연 시계 되돌리기. 지금은 실제 시각을 쓰므로 아무 것도 하지 않는다 */
    public void restart() {
    }

    /** 더미 데이터 경계 시각을 바꾼다 */
    public void restartAt(Instant newSimStart) {
        this.simStart = newSimStart;
    }
}
