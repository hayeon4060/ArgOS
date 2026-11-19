package com.gte.argos.factory.judge;

import com.gte.argos.factory.judge.JudgementListener.TrendPoint;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 판정 엔진: 센서값을 받아 1분 추이, 센서 이벤트, 관제 단위 경보를 만든다.
 * 스프링에 기대지 않는 순수 Java 라서 7일치 가져오기·실시간·테스트가 같은 코드를 쓴다.
 *
 * <p>스레드 안전하지 않다. 실시간에서는 한 스레드(또는 synchronized)로만 부른다.
 */
public final class JudgementEngine {

    private final Map<String, SensorJudge> judgesByTag = new HashMap<>();
    private final Map<String, TrendAggregator> trendsByTag = new HashMap<>();
    private final JudgementListener listener;
    private final AlarmCorrelator correlator;
    private final int recoverSamples;
    private final AtomicLong eventKeys = new AtomicLong(1);
    private final AtomicLong alarmKeys = new AtomicLong(1);
    private long unknownTagCount;

    /**
     * @param sensors        판정할 센서들
     * @param listener       결과를 받을 곳
     * @param options        판정 옵션
     */
    public JudgementEngine(Collection<SensorDef> sensors, JudgementListener listener, Options options) {
        this.listener = listener;
        this.correlator = new AlarmCorrelator(listener, alarmKeys::getAndIncrement, options.autoCloseAlarms());
        this.recoverSamples = options.recoverSamples();
        for (SensorDef def : sensors) {
            judgesByTag.put(def.tag(), new SensorJudge(def, options.recoverSamples()));
            trendsByTag.put(def.tag(), new TrendAggregator(def.sensorId()));
        }
    }

    /** 값 하나를 넣는다. 등록되지 않은 태그는 세기만 하고 버린다 */
    public void accept(SensorReading reading) {
        SensorJudge judge = judgesByTag.get(reading.tag());
        if (judge == null) {
            unknownTagCount++;
            return;
        }

        listener.onReading(judge.def().sensorId(), reading.ts(), reading.value());
        TrendPoint finished = trendsByTag.get(reading.tag()).accept(reading.ts(), reading.value());
        if (finished != null) {
            listener.onTrend(finished);
        }

        SensorJudge.Result r = judge.accept(reading.ts(), reading.value(), eventKeys::getAndIncrement);
        SensorDef def = judge.def();
        switch (r.kind()) {
            case NONE -> {
                return;
            }
            case OPENED -> {
                listener.onEventOpened(r.eventKey(), def.sensorId(), def.rule().thresholdId(), r.stage(),
                        r.startedAt(), r.detectedAt(), r.lowSide(), r.rule(), r.backlog());
                correlator.onEventOpened(def, r.eventKey(), r.stage(), r.lowSide(), r.detectedAt());
            }
            case SAMPLE -> {
                listener.onEventSample(r.eventKey(), r.sample());
                if (r.escalated()) {
                    listener.onEventEscalated(r.eventKey(), r.escalatedTo(), r.sample().ts());
                    correlator.onEventEscalated(def, r.eventKey(), r.escalatedTo(), r.sample().ts());
                }
                if (r.closed()) {
                    listener.onEventClosed(r.eventKey(), r.endedAt(), r.peak());
                    correlator.onEventClosed(def, r.eventKey(), r.endedAt());
                }
            }
        }
    }

    /** 밖(VMS)에서 판정한 센서의 진행 중 이벤트: 태그 → 상태 */
    private final Map<String, ExternalEvent> externalByTag = new HashMap<>();

    /**
     * 밖에서 단계를 판정해 보내는 센서 (열화상 IR-301: VMS 가 판정). 엔진은 다시 판정하지 않고 받은 단계로
     * 이벤트·경보를 만든다. 값은 1분 추이에 그대로 들어간다.
     * <pre>
     * 정상 → 2·3 : 이벤트 시작 (rule = IR)      2 → 3 : 단계 올림
     * 2·3 → 1    : 이벤트 끝 (VMS 가 복귀 조건 20초를 이미 채운 뒤 보냄)
     * 3 → 2      : 무시 (VMS 는 3 에서 바로 1 로 돌아옴)
     * </pre>
     */
    public void acceptExternal(SensorReading reading, Stage stage) {
        SensorJudge judge = judgesByTag.get(reading.tag());
        if (judge == null) {
            unknownTagCount++;
            return;
        }
        SensorDef def = judge.def();
        listener.onReading(def.sensorId(), reading.ts(), reading.value());
        TrendPoint finished = trendsByTag.get(reading.tag()).accept(reading.ts(), reading.value());
        if (finished != null) {
            listener.onTrend(finished);
        }
        JudgementListener.Sample sample = new JudgementListener.Sample(reading.ts(), reading.value());
        ExternalEvent open = externalByTag.get(reading.tag());
        if (open == null) {
            if (stage == Stage.NORMAL) {
                return;
            }
            long key = eventKeys.getAndIncrement();
            externalByTag.put(reading.tag(), new ExternalEvent(key, stage, reading.value()));
            listener.onEventOpened(key, def.sensorId(), def.rule().thresholdId(), stage, reading.ts(), reading.ts(),
                    false, "IR", java.util.List.of(sample));
            correlator.onEventOpened(def, key, stage, false, reading.ts());
            return;
        }
        listener.onEventSample(open.key, sample);
        open.peak = Math.max(open.peak, reading.value());
        if (stage == Stage.NORMAL) {
            externalByTag.remove(reading.tag());
            listener.onEventClosed(open.key, reading.ts(), open.peak);
            correlator.onEventClosed(def, open.key, reading.ts());
        } else if (stage.code() > open.stage.code()) {
            open.stage = stage;
            listener.onEventEscalated(open.key, stage, reading.ts());
            correlator.onEventEscalated(def, open.key, stage, reading.ts());
        }
    }

    private static final class ExternalEvent {
        final long key;
        Stage stage;
        double peak;

        ExternalEvent(long key, Stage stage, double peak) {
            this.key = key;
            this.stage = stage;
            this.peak = peak;
        }
    }

    /** 모으던 1분 추이를 모두 내보낸다 (데이터 끝, 서버 종료 때). 진행 중 이벤트는 그대로 둔다 */
    public void flushTrends() {
        for (TrendAggregator agg : trendsByTag.values()) {
            TrendPoint p = agg.flush();
            if (p != null) {
                listener.onTrend(p);
            }
        }
    }

    /**
     * 기준값·센서 정보를 바꾼다 (기준값 저장 뒤). 진행 중 판정과 경보는 그대로 둔다.
     * 새 센서는 추가하고, 목록에 없는 센서는 더 이상 받지 않는다.
     */
    public void updateDefinitions(Collection<SensorDef> sensors) {
        java.util.Set<String> keep = new java.util.HashSet<>();
        for (SensorDef def : sensors) {
            keep.add(def.tag());
            SensorJudge judge = judgesByTag.get(def.tag());
            if (judge != null) {
                judge.updateDef(def);
            } else {
                judgesByTag.put(def.tag(), new SensorJudge(def, recoverSamples));
                trendsByTag.put(def.tag(), new TrendAggregator(def.sensorId()));
            }
        }
        judgesByTag.keySet().retainAll(keep);
        trendsByTag.keySet().retainAll(keep);
    }

    /** 사람이 경보를 닫았을 때 엔진에도 알린다 (실시간) */
    public void closeAlarm(long unitId) {
        correlator.closeAlarm(unitId);
    }

    /** 이 태그 센서의 진행 중 이벤트 단계. 없으면 NORMAL */
    public Stage currentStage(String tag) {
        ExternalEvent ext = externalByTag.get(tag);
        if (ext != null) {
            return ext.stage;
        }
        SensorJudge judge = judgesByTag.get(tag);
        return judge != null && judge.hasOpenEvent() ? judge.openStage() : Stage.NORMAL;
    }

    public long unknownTagCount() {
        return unknownTagCount;
    }

    /**
     * @param recoverSamples  이벤트 중 정상 값이 몇 개 이어지면 끝으로 볼지 (기본 6 = 30초)
     * @param autoCloseAlarms 이벤트가 모두 끝나면 경보도 닫을지 (과거 데이터 = true, 실시간 = false)
     */
    public record Options(int recoverSamples, boolean autoCloseAlarms) {

        public Options {
            if (recoverSamples < 1) {
                throw new IllegalArgumentException("recoverSamples 는 1 이상");
            }
        }

        public static Options history() {
            return new Options(6, true);
        }

        public static Options live() {
            return new Options(6, false);
        }
    }
}
