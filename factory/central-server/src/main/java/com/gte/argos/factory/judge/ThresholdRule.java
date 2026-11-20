package com.gte.argos.factory.judge;

/**
 * 센서 하나에 지금 적용 중인 기준값 (sensor_threshold 한 줄).
 * 상한(warn/alarm)과 하한(lowWarn/lowAlarm)은 비어 있을 수 있다.
 * 비교는 "이상/이하"(경계값 포함)로 한다.
 *
 * @param thresholdId    sensor_threshold.threshold_id (이벤트에 판정 근거로 남김)
 * @param warn           주의 상한
 * @param alarm          경보 상한
 * @param lowWarn        주의 하한
 * @param lowAlarm       경보 하한
 * @param warnSamples    주의로 판정하려면 연속으로 넘어야 하는 샘플 수
 * @param alarmSamples   경보로 판정하려면 연속으로 넘어야 하는 샘플 수
 * @param releaseSamples 이벤트 중 정상 값이 몇 개 이어지면 끝으로 볼지. null 이면 엔진 기본값
 * @param releaseMargin  복귀 여유 (0.05 = 기준값의 5% 안쪽으로 돌아와야 정상). 0 이면 기준 아래면 정상
 * @param immediate      이 값 이상이면 지속 조건 없이 바로 경보 (v5 rule IMMEDIATE). null 이면 안 씀
 */
public record ThresholdRule(
        long thresholdId,
        Double warn,
        Double alarm,
        Double lowWarn,
        Double lowAlarm,
        int warnSamples,
        int alarmSamples,
        Integer releaseSamples,
        double releaseMargin,
        Double immediate) {

    public ThresholdRule {
        if (warnSamples < 1 || alarmSamples < 1) {
            throw new IllegalArgumentException("지속 샘플 수는 1 이상이어야 합니다");
        }
        if (releaseSamples != null && releaseSamples < 1) {
            throw new IllegalArgumentException("복귀 샘플 수는 1 이상이어야 합니다");
        }
        if (releaseMargin < 0 || releaseMargin >= 1) {
            throw new IllegalArgumentException("복귀 여유는 0 이상 1 미만이어야 합니다");
        }
    }

    /** 복귀 횟수·여유·즉시값 없이 (기존 기준값) */
    public ThresholdRule(long thresholdId, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                         int warnSamples, int alarmSamples) {
        this(thresholdId, warn, alarm, lowWarn, lowAlarm, warnSamples, alarmSamples, null, 0, null);
    }

    /** 값 하나가 어느 단계 범위에 있는지 (지속 조건은 보지 않음) */
    public Stage classify(double v) {
        if ((alarm != null && v >= alarm) || (lowAlarm != null && v <= lowAlarm) || isImmediate(v)) {
            return Stage.ALARM;
        }
        if ((warn != null && v >= warn) || (lowWarn != null && v <= lowWarn)) {
            return Stage.WARN;
        }
        return Stage.NORMAL;
    }

    /** 바로 경보로 볼 값인지 (immediate 이상) */
    public boolean isImmediate(double v) {
        return immediate != null && v >= immediate;
    }

    /**
     * 이벤트 중 이 값을 "정상으로 돌아옴"으로 셀 수 있는지.
     * 기준 범위 안이면서 복귀 여유만큼 더 안쪽이어야 한다 (상한은 warn × (1 - 여유) 미만, 하한은 lowWarn × (1 + 여유) 초과).
     */
    public boolean isRecovered(double v) {
        if (classify(v) != Stage.NORMAL) {
            return false;
        }
        if (releaseMargin <= 0) {
            return true;
        }
        if (warn != null && v >= warn - Math.abs(warn) * releaseMargin) {
            return false;
        }
        return lowWarn == null || v > lowWarn + Math.abs(lowWarn) * releaseMargin;
    }

    /** 하한 쪽으로 벗어난 값인지 (전류 저하 등). 최고값 대신 최저값을 기록할 때 쓴다 */
    public boolean isLowSide(double v) {
        boolean low = (lowWarn != null && v <= lowWarn) || (lowAlarm != null && v <= lowAlarm);
        boolean high = (warn != null && v >= warn) || (alarm != null && v >= alarm) || isImmediate(v);
        return low && !high;
    }
}
