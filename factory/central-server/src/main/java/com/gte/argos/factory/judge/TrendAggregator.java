package com.gte.argos.factory.judge;

import com.gte.argos.factory.judge.JudgementListener.TrendPoint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 센서 하나의 값을 1분 단위(평균·최소·최대·개수·통신 끊김 수)로 묶는다.
 * 분이 바뀌는 값이 들어오면 앞 분을 내보낸다.
 */
final class TrendAggregator {

    private final long sensorId;
    private Instant minute;
    private double sum;
    private double min;
    private double max;
    private int n;
    private int lost;

    TrendAggregator(long sensorId) {
        this.sensorId = sensorId;
    }

    /** 값을 더한다. 앞 분이 끝났으면 그 분의 결과를, 아니면 null 을 돌려준다 */
    TrendPoint accept(Instant ts, double value) {
        TrendPoint finished = moveTo(ts);
        sum += value;
        min = Math.min(min, value);
        max = Math.max(max, value);
        n++;
        return finished;
    }

    /** 통신 끊김(COMM_LOST) 한 번을 센다 (sensor_trend.lost_n). 앞 분이 끝났으면 그 분의 결과 */
    TrendPoint lost(Instant ts) {
        TrendPoint finished = moveTo(ts);
        lost++;
        return finished;
    }

    private TrendPoint moveTo(Instant ts) {
        Instant m = ts.truncatedTo(ChronoUnit.MINUTES);
        TrendPoint finished = null;
        if (minute != null && !minute.equals(m)) {
            finished = flush();
        }
        if (minute == null) {
            minute = m;
            sum = 0;
            min = Double.POSITIVE_INFINITY;
            max = Double.NEGATIVE_INFINITY;
            n = 0;
            lost = 0;
        }
        return finished;
    }

    /** 모으던 분을 내보낸다. 비어 있으면 null. 값 없이 끊김만 있으면 평균·최소·최대는 null */
    TrendPoint flush() {
        if (minute == null || (n == 0 && lost == 0)) {
            minute = null;
            return null;
        }
        TrendPoint point = n == 0
                ? new TrendPoint(sensorId, minute, null, null, null, 0, lost)
                : new TrendPoint(sensorId, minute, round(sum / n), min, max, n, lost);
        minute = null;
        return point;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
