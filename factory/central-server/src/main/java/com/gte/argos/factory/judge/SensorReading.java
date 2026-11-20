package com.gte.argos.factory.judge;

import java.time.Instant;
import java.util.Objects;

/**
 * 센서값 한 건. 7일치 CSV, 실시간 입력(Redis 봇 예정) 모두 이 모양으로 들어온다.
 *
 * @param tag   센서 태그 (예: TE-305)
 * @param ts    측정 시각
 * @param value 측정값
 */
public record SensorReading(String tag, Instant ts, double value) {

    public SensorReading {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(ts, "ts");
    }
}
