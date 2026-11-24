package com.gte.argos.factory.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JudgementEngineTest {

    private static final Instant T0 = Instant.parse("2026-11-16T00:00:00Z");

    // 상도 건조로 단위: 온도 TE-305(설정 140), 배기팬 IT-307(평균 18), 버너실 AT-303
    private final SensorDef te = new SensorDef(305, "TE-305", "TE", 5,
            new ThresholdRule(5, 150.0, 160.0, null, null, 24, 6),
            List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.FIRE))));
    private final SensorDef it = new SensorDef(317, "IT-307", "IT", 5,
            new ThresholdRule(10, 19.8, 22.5, 16.2, 12.6, 24, 6),
            List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.FIRE, Hazard.GAS))));
    private final SensorDef at = new SensorDef(323, "AT-303", "AT", 5,
            new ThresholdRule(13, 15.0, 30.0, null, null, 24, 12),
            List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.GAS))));

    private Recorder rec;
    private JudgementEngine engine;
    private int tick;

    @BeforeEach
    void setUp() {
        rec = new Recorder();
        engine = new JudgementEngine(List.of(te, it, at), rec, JudgementEngine.Options.history());
        tick = 0;
    }

    @Test
    void 주의_범위가_24샘플_이어지면_이벤트가_시작되고_정상_6샘플이면_끝난다() {
        feed("TE-305", 140, 10);
        feed("TE-305", 152, 24);   // 2분
        feed("TE-305", 140, 6);    // 30초

        assertThat(rec.log).containsExactly(
                "event+ 305 WARN start=10 detect=33 backlog=24",
                "alarm+ unit=5 WARN FIRE",
                "link",
                "event- end=34 peak=152.0",
                "alarm- closed=true");
    }

    @Test
    void 지속_조건보다_짧게_넘으면_이벤트가_없다() {
        feed("TE-305", 155, 23);
        feed("TE-305", 140, 10);
        feed("TE-305", 155, 23);

        assertThat(rec.log).isEmpty();
    }

    @Test
    void 경보_범위가_6샘플_이어지면_주의_이벤트가_경보로_오른다() {
        feed("TE-305", 152, 24);   // 주의 시작
        feed("TE-305", 165, 6);    // 경보

        assertThat(rec.log).containsSubsequence(
                "event+ 305 WARN start=0 detect=23 backlog=24",
                "event^ ALARM",
                "alarm~ ALARM FIRE");
    }

    @Test
    void 처음부터_경보_범위면_6샘플만에_경보_이벤트() {
        feed("TE-305", 170, 6);

        assertThat(rec.log).containsExactly(
                "event+ 305 ALARM start=0 detect=5 backlog=6",
                "alarm+ unit=5 ALARM FIRE",
                "link");
    }

    @Test
    void 배기팬_전류_저하와_CO_상승은_같은_가스_경보로_묶인다() {
        // 같은 시각에 두 센서 값을 번갈아 넣는다
        for (int i = 0; i < 30; i++) {
            Instant ts = T0.plusSeconds(5L * i);
            engine.accept(new SensorReading("IT-307", ts, 15.5));  // 평균 18 의 86% → 주의(하한)
            engine.accept(new SensorReading("AT-303", ts, 18.0));  // 15 ppm 이상 → 주의
        }

        assertThat(rec.log).containsExactly(
                "event+ 317 WARN start=0 detect=23 backlog=24",
                "alarm+ unit=5 WARN GAS",
                "link",
                "event+ 323 WARN start=0 detect=23 backlog=24",
                "link");
        assertThat(rec.lowSide).containsExactly(true, false);
        assertThat(rec.alarmDetailsChanges).isEqualTo(1);
    }

    @Test
    void 전류_상한_이벤트만으로_화재_유형_경보를_연다() {
        engine = new JudgementEngine(List.of(it), rec, JudgementEngine.Options.live());

        edge("IT-307", "current-high", EdgeStage.Phase.START, Stage.ALARM, "HI", 24.0, null, null);

        assertThat(rec.log).filteredOn(line -> line.startsWith("alarm"))
                .containsExactly("alarm+ unit=5 ALARM FIRE");
    }

    @Test
    void 분류할_수_없는_센서_이벤트는_경보를_열지_않는다() {
        SensorDef unknown = new SensorDef(399, "XX-399", "XX", 5,
                new ThresholdRule(5, 150.0, 160.0, null, null, 24, 6),
                List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.FIRE))));
        engine = new JudgementEngine(List.of(unknown, at), rec, JudgementEngine.Options.live());

        edge("XX-399", "unknown", EdgeStage.Phase.START, Stage.WARN, "HI", 152.0, null, null);
        assertThat(rec.log).contains("event+ 399 WARN start=0 detect=0 backlog=1");
        assertThat(rec.log).noneMatch(line -> line.startsWith("alarm") || line.equals("link"));

        edge("AT-303", "co", EdgeStage.Phase.START, Stage.WARN, "HI", 18.0, null, null);
        assertThat(rec.log).filteredOn(line -> line.startsWith("alarm"))
                .containsExactly("alarm+ unit=5 WARN GAS");
    }

    @Test
    void 일분마다_추이가_나온다() {
        feed("TE-305", 140, 12);   // 00:00:00 ~ 00:00:55
        feed("TE-305", 142, 12);   // 00:01:00 ~ 00:01:55
        engine.flushTrends();

        assertThat(rec.trends).hasSize(2);
        assertThat(rec.trends.get(0).n()).isEqualTo(12);
        assertThat(rec.trends.get(0).avg()).isEqualTo(140.0);
        assertThat(rec.trends.get(1).minute()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void 값이_15초_넘게_비면_연속_카운트를_다시_센다() {
        feed("TE-305", 152, 20);
        tick += 10;                // 50초 공백
        feed("TE-305", 152, 20);

        assertThat(rec.log).isEmpty();
    }

    @Test
    void 이벤트_규칙은_상한이면_HI_하한이면_LO() {
        for (int i = 0; i < 24; i++) {
            Instant ts = T0.plusSeconds(5L * i);
            engine.accept(new SensorReading("IT-307", ts, 15.5));  // 하한 주의
            engine.accept(new SensorReading("TE-305", ts, 152));   // 상한 주의
        }

        assertThat(rec.rules).containsExactly("LO", "HI");
    }

    @Test
    void 즉시_경보_값이면_지속_조건_없이_바로_경보() {
        SensorDef co = new SensorDef(323, "AT-303", "AT", 5,
                new ThresholdRule(13, 15.0, 30.0, null, null, 24, 12, null, 0, 200.0),
                List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.GAS))));
        engine = new JudgementEngine(List.of(co), rec, JudgementEngine.Options.history());

        feed("AT-303", 10, 3);
        feed("AT-303", 210, 1);

        assertThat(rec.log).containsExactly(
                "event+ 323 ALARM start=3 detect=3 backlog=1",
                "alarm+ unit=5 ALARM GAS",
                "link");
        assertThat(rec.rules).containsExactly("IMMEDIATE");
    }

    @Test
    void 주의_중_즉시_경보_값이_오면_바로_경보로_오른다() {
        SensorDef co = new SensorDef(323, "AT-303", "AT", 5,
                new ThresholdRule(13, 15.0, 30.0, null, null, 24, 12, null, 0, 200.0),
                List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.GAS))));
        engine = new JudgementEngine(List.of(co), rec, JudgementEngine.Options.history());

        feed("AT-303", 18, 24);
        feed("AT-303", 220, 1);

        assertThat(rec.log).containsSubsequence("event+ 323 WARN start=0 detect=23 backlog=24", "event^ ALARM");
    }

    @Test
    void 복귀_여유가_있으면_기준_바로_아래_값은_정상으로_세지_않는다() {
        SensorDef oven = new SensorDef(305, "TE-305", "TE", 5,
                new ThresholdRule(5, 150.0, 160.0, null, null, 24, 6, null, 0.05, null),
                List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.FIRE))));
        engine = new JudgementEngine(List.of(oven), rec, JudgementEngine.Options.history());

        feed("TE-305", 152, 24);   // 주의 시작
        feed("TE-305", 145, 10);   // 150 × 0.95 = 142.5 이상 → 아직 복귀 아님
        assertThat(rec.log).doesNotContain("alarm- closed=true");

        feed("TE-305", 140, 6);    // 142.5 미만 6개 → 끝
        assertThat(rec.log).contains("event- end=34 peak=152.0", "alarm- closed=true");
    }

    @Test
    void 복귀_횟수는_기준값의_release_samples_를_쓴다() {
        SensorDef oven = new SensorDef(305, "TE-305", "TE", 5,
                new ThresholdRule(5, 150.0, 160.0, null, null, 24, 6, 2, 0, null),
                List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.FIRE))));
        engine = new JudgementEngine(List.of(oven), rec, JudgementEngine.Options.history());

        feed("TE-305", 152, 24);
        feed("TE-305", 140, 2);

        assertThat(rec.log).contains("event- end=24 peak=152.0");
    }

    /** 같은 값을 5초 간격으로 count 번 넣는다. tick 은 0부터 이어진다 */
    private void feed(String tag, double value, int count) {
        for (int i = 0; i < count; i++) {
            engine.accept(new SensorReading(tag, T0.plusSeconds(5L * tick), value));
            tick++;
        }
    }

    private static int idx(Instant ts) {
        return (int) ((ts.getEpochSecond() - T0.getEpochSecond()) / 5);
    }

    @Test
    void 등록된_센서_값은_판정_전에_모두_onReading_으로_전달된다() {
        feed("TE-305", 140, 3);
        feed("XX-999", 1, 2);
        assertThat(rec.readings).isEqualTo(3);
    }

    @Test
    void 밖에서_판정한_열화상_단계로_이벤트와_화재_경보를_만든다() {
        SensorDef ir = new SensorDef(331, "IR-301", "IR", 5,
                new ThresholdRule(16, 50.0, 60.0, null, null, 24, 6),
                List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.FIRE))));
        engine = new JudgementEngine(List.of(ir), rec, JudgementEngine.Options.live());
        external(46.1, Stage.NORMAL);
        external(64.9, Stage.ALARM);      // 1 → 3 바로 (2단계 진입은 따로 안 옴)
        external(66.0, Stage.ALARM);
        external(48.0, Stage.NORMAL);     // VMS 가 복귀 조건을 채운 뒤 1
        assertThat(rec.rules).containsExactly("IR");
        assertThat(String.join(" | ", rec.log)).contains("event+ 331 ALARM").contains("alarm+ unit=5 ALARM FIRE")
                .contains("event- end=3 peak=66.0");
        assertThat(rec.readings).isEqualTo(4);
    }

    @Test
    void 밖에서_판정한_단계가_2에서_3으로_오르면_올림() {
        SensorDef ir = new SensorDef(331, "IR-301", "IR", 5,
                new ThresholdRule(16, 50.0, 60.0, null, null, 24, 6),
                List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.FIRE))));
        engine = new JudgementEngine(List.of(ir), rec, JudgementEngine.Options.live());
        external(55, Stage.WARN);
        external(61, Stage.ALARM);
        external(59, Stage.WARN);         // 3 → 2 는 무시
        assertThat(engine.currentStage("IR-301")).isEqualTo(Stage.ALARM);
        assertThat(String.join(" | ", rec.log)).contains("event^ ALARM");
    }

    private void edge(String tag, String key, EdgeStage.Phase phase, Stage stage, String rule, Double value,
                      Double peak, Boolean plcOk) {
        engine.acceptEdgeStage(new EdgeStage(tag, key, phase, stage, rule, 5L, value, peak, plcOk,
                T0.plusSeconds(5L * tick)));
        tick++;
    }

    private void edgeValue(String tag, double value) {
        engine.acceptValue(new SensorReading(tag, T0.plusSeconds(5L * tick), value));
        tick++;
    }

    @Test
    void 엣지_END_가_안_와도_정상_값이_20번_이어지면_이벤트를_닫는다() {
        engine = new JudgementEngine(List.of(te, it, at), rec, JudgementEngine.Options.live());
        edge("TE-305", "K1", EdgeStage.Phase.START, Stage.ALARM, "HI", 170.0, null, null);
        for (int i = 0; i < 19; i++) {
            edgeValue("TE-305", 100);
        }
        assertThat(engine.currentStage("TE-305")).isEqualTo(Stage.ALARM);
        edgeValue("TE-305", 100);
        assertThat(engine.currentStage("TE-305")).isEqualTo(Stage.NORMAL);
    }

    @Test
    void 엣지_값은_판정하지_않고_추이와_현재값만() {
        engine = new JudgementEngine(List.of(te, it, at), rec, JudgementEngine.Options.live());
        for (int i = 0; i < 40; i++) {
            edgeValue("TE-305", 170);   // 경보 범위지만 엣지가 판정하므로 이벤트 없음
        }
        engine.flushTrends();
        assertThat(rec.log).isEmpty();
        assertThat(rec.readings).isEqualTo(40);
        assertThat(rec.trends).isNotEmpty();
    }

    @Test
    void 엣지_단계로_이벤트를_열고_올리고_닫는다_경보는_관제_단위로() {
        engine = new JudgementEngine(List.of(te, it, at), rec, JudgementEngine.Options.live());
        String k = "P5-TE-305-20261116T090145";
        edge("TE-305", k, EdgeStage.Phase.START, Stage.WARN, "HI", 150.8, null, true);
        edge("TE-305", k, EdgeStage.Phase.START, Stage.WARN, "HI", 150.8, null, true); // 다시 온 START 는 무시
        edgeValue("TE-305", 158);
        edge("TE-305", k, EdgeStage.Phase.UPDATE, Stage.ALARM, "HI", 160.3, null, true);
        edge("TE-305", k, EdgeStage.Phase.UPDATE, Stage.WARN, "HI", 158.0, null, true); // 3 → 2
        edge("TE-305", k, EdgeStage.Phase.END, Stage.NORMAL, "HI", 144.6, 170.2, true);

        assertThat(rec.log).containsExactly(
                "event+ 305 WARN start=0 detect=0 backlog=1",
                "edge " + k + " plc=true",
                "alarm+ unit=5 WARN FIRE",
                "link",
                "edge null plc=true",
                "edge null plc=true",
                "event^ ALARM",
                "alarm~ ALARM FIRE",
                "edge null plc=true",
                "event_ WARN",
                "edge null plc=true",
                "event- end=5 peak=170.2",
                "alarm- closed=false");
        assertThat(engine.currentStage("TE-305")).isEqualTo(Stage.NORMAL);
    }

    @Test
    void 엣지_CO_와_전류_저하는_같은_가스_경보로_묶인다() {
        engine = new JudgementEngine(List.of(te, it, at), rec, JudgementEngine.Options.live());
        edge("AT-303", "P5-AT-303-1", EdgeStage.Phase.START, Stage.WARN, "HI", 18.0, null, null);
        edge("IT-307", "P5-IT-307-1", EdgeStage.Phase.START, Stage.ALARM, "LO", 12.0, null, null);

        assertThat(rec.log).filteredOn(l -> l.startsWith("alarm")).containsExactly(
                "alarm+ unit=5 WARN GAS", "alarm~ ALARM GAS");
        assertThat(rec.lowSide).containsExactly(false, true);
    }

    @Test
    void END_가_빠지고_새_구간이_오면_앞_구간을_닫는다_모르는_END_는_DB_로() {
        engine = new JudgementEngine(List.of(te, it, at), rec, JudgementEngine.Options.live());
        edge("TE-305", "A", EdgeStage.Phase.START, Stage.WARN, "HI", 151.0, null, null);
        edge("TE-305", "B", EdgeStage.Phase.START, Stage.WARN, "HI", 152.0, null, null);
        edge("TE-305", "C", EdgeStage.Phase.END, Stage.NORMAL, "HI", 140.0, 155.0, null);

        assertThat(rec.log).filteredOn(l -> l.startsWith("event")).containsExactly(
                "event+ 305 WARN start=0 detect=0 backlog=1",
                "event- end=1 peak=151.0",
                "event+ 305 WARN start=1 detect=1 backlog=1");
        assertThat(rec.log).contains("edge-unknown C");
    }

    @Test
    void 다시_켜면_열린_엣지_이벤트와_경보를_이어_받아_새_경보를_만들지_않는다() {
        engine = new JudgementEngine(List.of(te, it, at), rec, JudgementEngine.Options.live());
        long ek = engine.nextEventKey();
        long ak = engine.nextAlarmKey();
        // DB 에 '-2' 꼬리가 붙어 저장된 엣지 구간
        engine.restore(List.of(new JudgementEngine.RestoredEvent(ek, "IT-307", Stage.ALARM, "HI", 23.0,
                        "P5-IT-307-20261116T090224-2", true)),
                List.of(new JudgementEngine.RestoredAlarm(ak, 5, Stage.ALARM, Hazard.FIRE, List.of(ek))));

        edge("IT-307", "P5-IT-307-20261116T090224", EdgeStage.Phase.UPDATE, Stage.ALARM, "HI", 23.5, null, true);
        edgeValue("IT-307", 23.8);
        edge("IT-307", "P5-IT-307-20261116T090224", EdgeStage.Phase.END, Stage.NORMAL, "HI", 18.0, 24.0, true);

        assertThat(rec.log).noneMatch(l -> l.startsWith("event+") || l.startsWith("alarm+"));
        assertThat(rec.log).contains("event- end=2 peak=24.0", "alarm- closed=false");
        assertThat(engine.currentStage("IT-307")).isEqualTo(Stage.NORMAL);
    }

    @Test
    void 다시_켜면_열린_열화상_이벤트에_VMS_값이_이어_붙는다() {
        SensorDef ir = new SensorDef(331, "IR-301", "IR", 5,
                new ThresholdRule(16, 50.0, 60.0, null, null, 24, 6),
                List.of(new SensorDef.UnitMembership(5, Set.of(Hazard.FIRE))));
        engine = new JudgementEngine(List.of(ir), rec, JudgementEngine.Options.live());
        long ek = engine.nextEventKey();
        engine.restore(List.of(new JudgementEngine.RestoredEvent(ek, "IR-301", Stage.ALARM, "IR", 64.0, "k", true)),
                List.of(new JudgementEngine.RestoredAlarm(engine.nextAlarmKey(), 5, Stage.ALARM, Hazard.FIRE, List.of(ek))));
        external(65.0, Stage.ALARM);
        external(66.0, Stage.ALARM);
        assertThat(rec.log).isEmpty();
        assertThat(engine.currentStage("IR-301")).isEqualTo(Stage.ALARM);
        external(48.0, Stage.NORMAL);
        assertThat(rec.log).containsExactly("event- end=2 peak=66.0", "alarm- closed=false");
    }

    @Test
    void 다시_켜면_서버_판정_이벤트도_이어서_복귀를_센다() {
        engine = new JudgementEngine(List.of(te, it, at), rec, JudgementEngine.Options.live());
        long ek = engine.nextEventKey();
        engine.restore(List.of(new JudgementEngine.RestoredEvent(ek, "TE-305", Stage.WARN, "HI", 152.0, "k", false)),
                List.of(new JudgementEngine.RestoredAlarm(engine.nextAlarmKey(), 5, Stage.WARN, Hazard.FIRE, List.of(ek))));
        feed("TE-305", 153, 3);
        feed("TE-305", 140, 6);
        assertThat(rec.log).containsExactly("event- end=3 peak=153.0", "alarm- closed=false");
    }

    @Test
    void 엣지_구간_키_꼬리() {
        assertThat(JudgementEngine.sameEdge("P5-TE-305-20261116T090145-2", "P5-TE-305-20261116T090145")).isTrue();
        assertThat(JudgementEngine.sameEdge("P5-TE-305-20261116T090145", "P5-TE-305-20261116T090145")).isTrue();
        assertThat(JudgementEngine.sameEdge("P5-TE-305-20261116T090145", "P5-TE-305-20261116T090150")).isFalse();
    }

    @Test
    void 통신_끊김은_추이의_lost_n_으로() {
        engine = new JudgementEngine(List.of(te), rec, JudgementEngine.Options.live());
        edgeValue("TE-305", 140);
        engine.acceptLost("TE-305", T0.plusSeconds(20));
        engine.acceptLost("TE-305", T0.plusSeconds(75));  // 다음 분: 값 없이 끊김만
        engine.flushTrends();

        assertThat(rec.trends).hasSize(2);
        assertThat(rec.trends.get(0).n()).isEqualTo(1);
        assertThat(rec.trends.get(0).lostN()).isEqualTo(1);
        assertThat(rec.trends.get(1).avg()).isNull();
        assertThat(rec.trends.get(1).lostN()).isEqualTo(1);
    }

    private void external(double value, Stage stage) {
        engine.acceptExternal(new SensorReading("IR-301", T0.plusSeconds(5L * tick), value), stage);
        tick++;
    }

    private static final class Recorder implements JudgementListener {
        int readings;
        int alarmDetailsChanges;

        @Override
        public void onReading(long sensorId, Instant ts, double value) {
            readings++;
        }

        final List<String> log = new ArrayList<>();
        final List<TrendPoint> trends = new ArrayList<>();
        final List<Boolean> lowSide = new ArrayList<>();
        final List<String> rules = new ArrayList<>();

        @Override
        public void onTrend(TrendPoint point) {
            trends.add(point);
        }

        @Override
        public void onEventOpened(long eventKey, long sensorId, long thresholdId, Stage stage, Instant startedAt,
                                  Instant detectedAt, boolean low, String rule, List<Sample> backlog) {
            log.add("event+ " + sensorId + " " + stage + " start=" + idx(startedAt) + " detect=" + idx(detectedAt)
                    + " backlog=" + backlog.size());
            lowSide.add(low);
            rules.add(rule);
        }

        @Override
        public void onEventEscalated(long eventKey, Stage stage, Instant at) {
            log.add("event^ " + stage);
        }

        @Override
        public void onEdgeEvent(long eventKey, String edgeKey, Boolean plcOk) {
            log.add("edge " + edgeKey + " plc=" + plcOk);
        }

        @Override
        public void onEventStageLowered(long eventKey, Stage stage, Instant at) {
            log.add("event_ " + stage);
        }

        @Override
        public void onEdgeEventEndedUnknown(String edgeKey, Instant endedAt, Double peak) {
            log.add("edge-unknown " + edgeKey);
        }

        @Override
        public void onEventClosed(long eventKey, Instant endedAt, double peak) {
            log.add("event- end=" + idx(endedAt) + " peak=" + peak);
        }

        @Override
        public void onAlarmOpened(long alarmKey, long unitId, Stage stage, Hazard hazard, Instant raisedAt) {
            log.add("alarm+ unit=" + unitId + " " + stage + " " + hazard);
        }

        @Override
        public void onAlarmEventLinked(long alarmKey, long eventKey) {
            log.add("link");
        }

        @Override
        public void onAlarmDetailsChanged(long alarmKey, Instant at) {
            alarmDetailsChanges++;
        }

        @Override
        public void onAlarmUpdated(long alarmKey, Stage stage, Hazard hazard, Instant at) {
            log.add("alarm~ " + stage + " " + hazard);
        }

        @Override
        public void onAlarmCleared(long alarmKey, Instant at, boolean closed) {
            log.add("alarm- closed=" + closed);
        }
    }
}
