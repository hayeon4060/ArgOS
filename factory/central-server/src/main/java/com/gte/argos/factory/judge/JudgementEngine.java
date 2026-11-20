package com.gte.argos.factory.judge;

import com.gte.argos.factory.judge.JudgementListener.TrendPoint;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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

    /**
     * 엣지(DAS)가 보낸 1초 값 (Kafka argos.edge.raw). 단계는 엣지가 판정하므로 여기서는 판정하지 않고
     * 1분 추이·알림 센서 값·진행 중 엣지 이벤트 샘플에만 넣는다.
     */
    public void acceptValue(SensorReading reading) {
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
        ExternalEvent open = externalByTag.get(reading.tag());
        if (open != null) {
            listener.onEventSample(open.key, new JudgementListener.Sample(reading.ts(), reading.value()));
            open.peak = open.lowSide ? Math.min(open.peak, reading.value()) : Math.max(open.peak, reading.value());
            // 엣지가 재시작하면 END 를 보내지 않아 이벤트가 영영 열려 있게 된다 (2026-11-20).
            // 엣지 이벤트 중 정상 값이 엣지 복귀 횟수만큼 이어지면 중앙이 닫는다
            ThresholdRule rule = judge.def().rule();
            if (open.edgeKey != null && rule != null) {
                open.normalRun = rule.isRecovered(reading.value()) ? open.normalRun + 1 : 0;
                if (open.normalRun >= EDGE_RELEASE_SAMPLES) {
                    closeExternal(judge.def(), open, reading.ts(), null);
                }
            }
        }
    }

    /** 엣지 END 없이 닫을 때 필요한 연속 정상 값 수 (엣지 복귀 기준 20회 = 20초) */
    static final int EDGE_RELEASE_SAMPLES = 20;

    /** 통신 끊김 (argos.edge.raw quality = COMM_LOST). 1분 추이의 lost_n 으로 센다 */
    public void acceptLost(String tag, java.time.Instant ts) {
        TrendAggregator agg = trendsByTag.get(tag);
        if (agg == null) {
            unknownTagCount++;
            return;
        }
        TrendPoint finished = agg.lost(ts);
        if (finished != null) {
            listener.onTrend(finished);
        }
    }

    /**
     * 엣지(DAS)가 판정한 단계 변경 (Kafka argos.edge.stage). 엔진은 다시 판정하지 않고 받은 단계로
     * 센서 이벤트를 열고·바꾸고·닫으며, 경보는 지금처럼 관제 단위로 묶어서(조합) 만든다.
     * <pre>
     * START  : 이벤트 시작 (같은 구간 키가 이미 열려 있으면 다시 온 것으로 보고 무시)
     *          다른 구간이 열려 있으면 (END 가 빠진 것) 그 구간을 먼저 닫는다
     * UPDATE : 단계 올림 → 경보에도 반영 / 단계 내림(3 → 2) → 이벤트 단계만 (경보는 내려가지 않음)
     *          열린 구간이 없으면 (서버가 도중에 켜짐) 그 단계로 새로 연다
     * END    : 이벤트 끝. 엣지 peak_value 를 쓴다
     * </pre>
     */
    public void acceptEdgeStage(EdgeStage s) {
        SensorJudge judge = judgesByTag.get(s.tag());
        if (judge == null) {
            unknownTagCount++;
            return;
        }
        SensorDef def = judge.def();
        ExternalEvent open = externalByTag.get(s.tag());
        if (open != null && !sameEdge(open.edgeKey, s.edgeKey()) && s.phase() != EdgeStage.Phase.END) {
            closeExternal(def, open, s.ts(), null); // 앞 구간의 END 가 빠짐
            open = null;
        }
        if (s.phase() == EdgeStage.Phase.END || s.stage() == Stage.NORMAL) {
            if (open != null && sameEdge(open.edgeKey, s.edgeKey())) {
                if (s.plcOk() != null) {
                    listener.onEdgeEvent(open.key, null, s.plcOk());
                }
                closeExternal(def, open, s.ts(), s.peak());
            } else {
                listener.onEdgeEventEndedUnknown(s.edgeKey(), s.ts(), s.peak());
            }
            return;
        }
        if (open == null) {
            boolean low = "LO".equals(s.rule());
            long key = eventKeys.getAndIncrement();
            double v = s.value() == null ? 0 : s.value();
            ExternalEvent ev = new ExternalEvent(key, s.stage(), v, low, s.edgeKey());
            externalByTag.put(s.tag(), ev);
            List<JudgementListener.Sample> first = s.value() == null ? List.of()
                    : List.of(new JudgementListener.Sample(s.ts(), s.value()));
            long thresholdId = s.thresholdId() != null ? s.thresholdId() : -1;
            listener.onEventOpened(key, def.sensorId(), thresholdId, s.stage(), s.ts(), s.ts(), low,
                    s.rule() == null ? (low ? "LO" : "HI") : s.rule(), first);
            listener.onEdgeEvent(key, s.edgeKey(), s.plcOk());
            correlator.onEventOpened(def, key, s.stage(), low, s.ts());
            return;
        }
        if (s.plcOk() != null) {
            listener.onEdgeEvent(open.key, null, s.plcOk());
        }
        if (s.stage().code() > open.stage.code()) {
            open.stage = s.stage();
            listener.onEventEscalated(open.key, s.stage(), s.ts());
            correlator.onEventEscalated(def, open.key, s.stage(), s.ts());
        } else if (s.stage().code() < open.stage.code()) {
            open.stage = s.stage();
            listener.onEventStageLowered(open.key, s.stage(), s.ts());
        }
    }

    private void closeExternal(SensorDef def, ExternalEvent open, java.time.Instant at, Double peak) {
        externalByTag.remove(def.tag());
        listener.onEventClosed(open.key, at, peak != null ? peak : open.peak);
        correlator.onEventClosed(def, open.key, at);
    }

    private static final class ExternalEvent {
        final long key;
        final boolean lowSide;
        /** 엣지 구간 키 (VMS 이벤트는 null) */
        final String edgeKey;
        Stage stage;
        double peak;
        /** 엣지 이벤트 중 이어진 정상 값 수 */
        int normalRun;

        ExternalEvent(long key, Stage stage, double peak) {
            this(key, stage, peak, false, null);
        }

        ExternalEvent(long key, Stage stage, double peak, boolean lowSide, String edgeKey) {
            this.key = key;
            this.stage = stage;
            this.peak = peak;
            this.lowSide = lowSide;
            this.edgeKey = edgeKey;
        }
    }

    /**
     * 서버를 다시 켤 때 DB 에 열린 채 남은 이벤트 · 경보를 이어 받는다 (다시 켤 때마다 경보가 새로 생기던 문제, 2026-11-20).
     * 밖(VMS · 엣지)에서 판정한 이벤트는 밖 이벤트로, 서버가 판정하던 이벤트는 센서 판정 상태로 넣는다.
     * 경보는 그 단위의 경보로 다시 묶어, 이어 오는 단계 메시지가 새 경보가 아니라 이 경보에 붙게 한다.
     */
    public void restore(List<RestoredEvent> events, List<RestoredAlarm> alarms) {
        Map<Long, RestoredEvent> byKey = new HashMap<>();
        for (RestoredEvent e : events) {
            SensorJudge judge = judgesByTag.get(e.tag());
            if (judge == null) {
                continue;
            }
            byKey.put(e.eventKey(), e);
            boolean low = "LO".equals(e.rule());
            if (e.external()) {
                externalByTag.put(e.tag(), new ExternalEvent(e.eventKey(), e.stage(), e.peak(), low, e.edgeKey()));
            } else {
                judge.restoreOpen(e.eventKey(), e.stage(), low, e.rule(), e.peak());
            }
        }
        for (RestoredAlarm a : alarms) {
            List<AlarmCorrelator.RestoredRef> refs = new java.util.ArrayList<>();
            for (Long k : a.eventKeys()) {
                RestoredEvent e = byKey.get(k);
                if (e != null) {
                    refs.add(new AlarmCorrelator.RestoredRef(judgesByTag.get(e.tag()).def(), k, e.stage(),
                            "LO".equals(e.rule())));
                }
            }
            correlator.restore(a.alarmKey(), a.unitId(), a.stage(), a.hazard(), refs);
        }
    }

    /** 이어 받을 이벤트에 붙일 엔진 번호 */
    public long nextEventKey() {
        return eventKeys.getAndIncrement();
    }

    /** 이어 받을 경보에 붙일 엔진 번호 */
    public long nextAlarmKey() {
        return alarmKeys.getAndIncrement();
    }

    /**
     * @param external 밖(VMS · 엣지) 판정 이벤트인지
     * @param edgeKey  엣지 구간 키 (DB 값, 겹쳐서 붙은 -2 같은 꼬리 포함 가능)
     */
    public record RestoredEvent(long eventKey, String tag, Stage stage, String rule, double peak, String edgeKey,
                                boolean external) {
    }

    public record RestoredAlarm(long alarmKey, long unitId, Stage stage, Hazard hazard, List<Long> eventKeys) {
    }

    /** 같은 엣지 구간인지. DB 키는 겹칠 때 '-2' 같은 꼬리가 붙어 있을 수 있다 */
    static boolean sameEdge(String stored, String incoming) {
        if (stored == null || incoming == null) {
            return java.util.Objects.equals(stored, incoming);
        }
        return stored.equals(incoming) || stored.matches(java.util.regex.Pattern.quote(incoming) + "-\\d+");
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
