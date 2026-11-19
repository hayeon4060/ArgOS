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

    private void external(double value, Stage stage) {
        engine.acceptExternal(new SensorReading("IR-301", T0.plusSeconds(5L * tick), value), stage);
        tick++;
    }

    private static final class Recorder implements JudgementListener {
        int readings;

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
        public void onAlarmUpdated(long alarmKey, Stage stage, Hazard hazard, Instant at) {
            log.add("alarm~ " + stage + " " + hazard);
        }

        @Override
        public void onAlarmCleared(long alarmKey, Instant at, boolean closed) {
            log.add("alarm- closed=" + closed);
        }
    }
}
