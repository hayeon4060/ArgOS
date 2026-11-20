package com.gte.argos.factory.judge;

import com.gte.argos.factory.judge.JudgementListener.Sample;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 센서 하나의 단계 판정 상태.
 *
 * <pre>
 * - 주의 범위 이상인 값이 warnSamples 개 연속   → 주의 이벤트 시작 (시작 시각 = 연속 구간의 첫 샘플)
 * - 경보 범위 값이 alarmSamples 개 연속         → 경보 이벤트 시작, 또는 진행 중 이벤트를 경보로 올림
 * - 즉시 경보 값(immediate) 이상이면              → 지속 조건 없이 바로 경보 (규칙 IMMEDIATE)
 * - 이벤트 중 정상 값이 복귀 횟수만큼 연속       → 이벤트 끝 (끝 시각 = 정상으로 돌아온 첫 샘플)
 *   복귀 횟수는 기준값의 release_samples (없으면 엔진 기본값), 정상은 복귀 여유만큼 기준 안쪽인 값
 * - 값이 sampleSec × 3 넘게 비면 연속 카운트를 처음부터 다시 셈
 * </pre>
 *
 * 스레드 안전하지 않다. 엔진이 한 스레드에서만 부른다.
 */
final class SensorJudge {

    /** 이벤트를 열기 전까지 쌓아 두는 샘플 수 상한 (메모리 보호) */
    private static final int BACKLOG_LIMIT = 2_000;

    private SensorDef def;
    private final int recoverSamples;
    private final Duration maxGap;

    private Instant lastTs;

    // 연속 카운트
    private int warnRun;          // 주의 이상(2·3단계) 연속 수
    private Instant warnRunStart;
    private int alarmRun;         // 경보(3단계) 연속 수
    private int normalRun;        // 이벤트 중 정상 연속 수
    private Instant normalRunStart;
    private final List<Sample> backlog = new ArrayList<>();

    // 진행 중 이벤트
    private long openEventKey = -1;
    private Stage openStage;
    private boolean openLowSide;
    private String openRule;
    private double openPeak;

    SensorJudge(SensorDef def, int recoverSamples) {
        this.def = def;
        this.recoverSamples = recoverSamples;
        this.maxGap = Duration.ofSeconds(Math.max(1, def.sampleSec()) * 3L);
    }

    SensorDef def() {
        return def;
    }

    /** 기준값만 바꾼다. 연속 카운트와 진행 중 이벤트는 그대로 이어진다 */
    void updateDef(SensorDef newDef) {
        this.def = newDef;
    }

    boolean hasOpenEvent() {
        return openEventKey >= 0;
    }

    long openEventKey() {
        return openEventKey;
    }

    Stage openStage() {
        return openStage;
    }

    /**
     * 값 하나를 판정한다.
     *
     * @param nextEventKey 이벤트를 새로 열 때 쓸 번호를 주는 함수
     */
    Result accept(Instant ts, double value, java.util.function.LongSupplier nextEventKey) {
        ThresholdRule rule = def.rule();
        if (rule == null) {
            lastTs = ts;
            return Result.NONE;
        }

        if (lastTs != null && Duration.between(lastTs, ts).compareTo(maxGap) > 0) {
            resetRuns();
        }
        lastTs = ts;

        Stage stage = rule.classify(value);
        Sample sample = new Sample(ts, value);

        // 연속 카운트 갱신
        if (stage.atLeast(Stage.WARN)) {
            if (warnRun == 0) {
                warnRunStart = ts;
                backlog.clear();
            }
            warnRun++;
            if (!hasOpenEvent() && backlog.size() < BACKLOG_LIMIT) {
                backlog.add(sample);
            }
        } else {
            warnRun = 0;
            warnRunStart = null;
            backlog.clear();
        }
        alarmRun = stage == Stage.ALARM ? alarmRun + 1 : 0;
        boolean immediate = rule.isImmediate(value);

        if (!hasOpenEvent()) {
            Stage reached = immediate ? Stage.ALARM : reachedStage(rule);
            if (reached == null) {
                return Result.NONE;
            }
            openEventKey = nextEventKey.getAsLong();
            openStage = reached;
            openLowSide = rule.isLowSide(value);
            openRule = immediate ? "IMMEDIATE" : openLowSide ? "LO" : "HI";
            // 경보 범위 값은 주의 이상 연속 구간에도 포함되므로 시작 시각은 항상 그 구간의 첫 샘플
            Instant startedAt = warnRunStart;
            List<Sample> opened = List.copyOf(backlog);
            openPeak = value;
            for (Sample s : opened) {
                openPeak = extreme(openPeak, s.value());
            }
            backlog.clear();
            normalRun = 0;
            return Result.opened(openEventKey, reached, startedAt, ts, openLowSide, openRule, opened);
        }

        // 이벤트 진행 중
        Result result = Result.sample(openEventKey, sample);
        if (!rule.isRecovered(value)) {
            if (stage != Stage.NORMAL) {
                openPeak = extreme(openPeak, value);
            }
            normalRun = 0;
            normalRunStart = null;
            if (openStage == Stage.WARN && (immediate || alarmRun >= rule.alarmSamples())) {
                openStage = Stage.ALARM;
                return result.withEscalation(Stage.ALARM);
            }
            return result;
        }

        if (normalRun == 0) {
            normalRunStart = ts;
        }
        normalRun++;
        if (normalRun >= recoverSamples(rule)) {
            Result closed = result.withClose(normalRunStart, openPeak);
            clearOpenEvent();
            return closed;
        }
        return result;
    }

    /** 서버를 다시 켤 때 DB 에 열린 채 남은 이벤트를 이어 받는다 (연속 카운트는 0 부터) */
    void restoreOpen(long eventKey, Stage stage, boolean lowSide, String rule, double peak) {
        openEventKey = eventKey;
        openStage = stage;
        openLowSide = lowSide;
        openRule = rule;
        openPeak = peak;
        normalRun = 0;
        normalRunStart = null;
    }

    /** 이벤트를 끝내는 데 필요한 정상 연속 수 */
    private int recoverSamples(ThresholdRule rule) {
        return rule.releaseSamples() != null ? rule.releaseSamples() : recoverSamples;
    }

    /** 지속 조건을 채운 단계 (경보가 우선). 아직 못 채웠으면 null */
    private Stage reachedStage(ThresholdRule rule) {
        if (alarmRun >= rule.alarmSamples()) {
            return Stage.ALARM;
        }
        if (warnRun >= rule.warnSamples()) {
            return Stage.WARN;
        }
        return null;
    }

    private double extreme(double current, double value) {
        return openLowSide ? Math.min(current, value) : Math.max(current, value);
    }

    private void resetRuns() {
        warnRun = 0;
        warnRunStart = null;
        alarmRun = 0;
        backlog.clear();
    }

    private void clearOpenEvent() {
        openEventKey = -1;
        openStage = null;
        openLowSide = false;
        openRule = null;
        openPeak = 0;
        normalRun = 0;
        normalRunStart = null;
        resetRuns();
    }

    /** 샘플 하나를 판정한 결과 */
    record Result(
            Kind kind,
            long eventKey,
            Stage stage,
            Instant startedAt,
            Instant detectedAt,
            boolean lowSide,
            String rule,
            List<Sample> backlog,
            Sample sample,
            Stage escalatedTo,
            Instant endedAt,
            double peak) {

        static final Result NONE = new Result(Kind.NONE, -1, null, null, null, false, null, List.of(), null, null, null, 0);

        enum Kind { NONE, OPENED, SAMPLE }

        static Result opened(long key, Stage stage, Instant startedAt, Instant detectedAt, boolean lowSide, String rule,
                             List<Sample> backlog) {
            return new Result(Kind.OPENED, key, stage, startedAt, detectedAt, lowSide, rule, backlog, null, null, null, 0);
        }

        static Result sample(long key, Sample sample) {
            return new Result(Kind.SAMPLE, key, null, null, null, false, null, List.of(), sample, null, null, 0);
        }

        Result withEscalation(Stage to) {
            return new Result(kind, eventKey, stage, startedAt, detectedAt, lowSide, rule, backlog, sample, to, endedAt, peak);
        }

        Result withClose(Instant ended, double peakValue) {
            return new Result(kind, eventKey, stage, startedAt, detectedAt, lowSide, rule, backlog, sample, escalatedTo, ended,
                    peakValue);
        }

        boolean escalated() {
            return escalatedTo != null;
        }

        boolean closed() {
            return endedAt != null;
        }
    }
}
