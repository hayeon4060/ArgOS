package com.gte.argos.factory.ingest;

import com.gte.argos.factory.command.FanAutoService;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.judge.EdgeStage;
import com.gte.argos.factory.judge.JudgementEngine;
import com.gte.argos.factory.judge.SensorDef;
import com.gte.argos.factory.judge.SensorReading;
import com.gte.argos.factory.judge.Stage;
import com.gte.argos.factory.live.FeedingListener;
import com.gte.argos.factory.live.LiveFeed;
import com.gte.argos.factory.live.LiveState;
import com.gte.argos.factory.master.SensorDefinitionLoader;
import com.gte.argos.factory.retention.SensorDailyService;
import com.gte.argos.factory.store.JdbcJudgementStore;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실시간 센서값 입구. 시연 시작(11/16 09:00) 이후의 값이 여기로 들어온다.
 *
 * <pre>
 * 입력 어댑터
 *   개발용 POST(/dev/readings) → ingest() → 판정 엔진이 단계까지 판정
 *   엣지 Kafka argos.edge.raw → ingestEdgeValue() → 1분 추이·현재값만 (판정 안 함)
 *   엣지 Kafka argos.edge.stage → ingestEdgeStage() → 엣지 단계로 센서 이벤트, 관제 단위 조합 경보
 *   VMS 열화상 → ingestExternal() → VMS 단계로 이벤트·경보
 *   → DB 저장 + 메모리(LiveState: 지금 값, 최근 1시간) + 피드(LiveFeed: 중앙 서버가 가져감)
 * 엣지·VMS 값의 시각은 받은 시각(서버 시계)으로 한다 (보낸 쪽 ts 는 쓰지 않음, 2026-11-20 결정)
 * </pre>
 *
 * 실시간 모드에서는 이벤트가 끝나도 경보를 닫지 않는다. 경보는 사람이 연계 서버에서 닫는다.
 */
@Service
public class LiveJudgementService {

    private static final Logger log = LoggerFactory.getLogger(LiveJudgementService.class);

    private final JdbcTemplate jdbc;
    private final SensorDefinitionLoader sensorLoader;
    private final DemoClock clock;
    private final LiveState state;
    private final LiveFeed feed;
    private final SensorDailyService daily;
    private final FanAutoService fanAuto;
    private final ApplicationEventPublisher events;

    private JudgementEngine engine;
    private FeedingListener listener;
    private Map<String, Long> sensorIdByTag = Map.of();

    public LiveJudgementService(JdbcTemplate jdbc, SensorDefinitionLoader sensorLoader, DemoClock clock,
                                LiveState state, LiveFeed feed, SensorDailyService daily, FanAutoService fanAuto,
                                ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
        this.fanAuto = fanAuto;
        this.daily = daily;
        this.sensorLoader = sensorLoader;
        this.clock = clock;
        this.state = state;
        this.feed = feed;
    }

    /** 값 여러 개를 시간 순서대로 판정·저장한다. 판정에 넣은 수를 돌려준다 */
    @Transactional
    public synchronized int ingest(Collection<SensorReading> readings) {
        JudgementEngine e = engine();
        List<Object[]> values = new ArrayList<>();
        int accepted = 0;
        for (SensorReading r : readings) {
            if (r.ts().isBefore(clock.simStart())) {
                continue; // 시연 시작 전 값은 7일치 몫
            }
            Long sensorId = sensorIdByTag.get(r.tag());
            if (sensorId != null) {
                state.record(sensorId, r.ts(), r.value());
                values.add(new Object[] {sensorId, odt(r.ts()), r.value()});
            }
            e.accept(r);
            accepted++;
        }
        listener.flush();
        for (FeedingListener.GasAlarm g : listener.drainGasAlarms()) {
            fanAuto.onGasAlarm(g.alarmId(), g.unitId(), g.stage());
        }
        if (!values.isEmpty()) {
            feed.append("SENSOR_VALUES", clock.nowOffset(), Map.of("values", values));
        }
        return accepted;
    }

    /**
     * 밖에서 단계를 판정한 값 하나 (VMS 열화상). 판정 엔진은 받은 단계로 이벤트·경보만 만든다.
     *
     * @return 판정에 넣었으면 true (시연 시작 전 값·모르는 태그면 false)
     */
    @Transactional
    public synchronized boolean ingestExternal(SensorReading r, Stage stage) {
        if (r.ts().isBefore(clock.simStart())) {
            return false;
        }
        JudgementEngine e = engine();
        Long sensorId = sensorIdByTag.get(r.tag());
        if (sensorId == null) {
            return false;
        }
        state.record(sensorId, r.ts(), r.value());
        e.acceptExternal(r, stage);
        listener.flush();
        feed.append("SENSOR_VALUES", clock.nowOffset(),
                Map.of("values", List.<Object[]>of(new Object[] {sensorId, odt(r.ts()), r.value()})));
        return true;
    }

    /**
     * 엣지(DAS) 1초 값 (Kafka argos.edge.raw). 시각은 받은 시각(서버 시계)으로 한다 (엣지 ts 는 쓰지 않음).
     * 단계 판정은 하지 않는다 (엣지가 판정해 argos.edge.stage 로 보냄).
     *
     * @param value null 이거나 lost 면 통신 끊김(COMM_LOST)으로 센다
     * @return 아는 태그면 true
     */
    @Transactional
    public synchronized boolean ingestEdgeValue(String tag, Double value, boolean lost) {
        JudgementEngine e = engine();
        Long sensorId = sensorIdByTag.get(tag);
        if (sensorId == null) {
            return false;
        }
        java.time.Instant now = clock.now();
        if (lost || value == null) {
            e.acceptLost(tag, now);
            listener.flush();
            return true;
        }
        state.record(sensorId, now, value);
        e.acceptValue(new SensorReading(tag, now, value));
        listener.flush();
        feed.append("SENSOR_VALUES", clock.nowOffset(),
                Map.of("values", List.<Object[]>of(new Object[] {sensorId, odt(now), value})));
        return true;
    }

    /**
     * 엣지(DAS) 단계 변경 (Kafka argos.edge.stage) → 센서 이벤트, 관제 단위 조합 경보, 가스면 배기팬 자동 제어.
     * s.ts() 는 받은 시각(서버 시계)이어야 한다.
     */
    @Transactional
    public synchronized void ingestEdgeStage(EdgeStage s) {
        engine().acceptEdgeStage(s);
        listener.flush();
        for (FeedingListener.GasAlarm g : listener.drainGasAlarms()) {
            fanAuto.onGasAlarm(g.alarmId(), g.unitId(), g.stage());
        }
    }

    /** 이 센서의 지금 단계 (진행 중 이벤트 기준) */
    public synchronized Stage currentStage(String tag) {
        return engine().currentStage(tag);
    }

    /** 사람이 경보를 닫았을 때. 이후 그 단위의 새 이벤트는 새 경보가 된다 */
    public synchronized void closeAlarm(long unitId) {
        engine().closeAlarm(unitId);
        listener.alarmClosed(unitId);
    }

    /**
     * 시연 리셋: 시계를 11/16 09:00 으로 되돌리고, 그 이후 생긴 판정 결과를 지운 뒤 엔진을 새로 만든다.
     * 7일치(09:00 이전)는 그대로 둔다.
     */
    @Transactional
    public synchronized void reset() {
        OffsetDateTime cutoff = clock.simStart().atZone(clock.zone()).toOffsetDateTime();
        jdbc.update("DELETE FROM equipment_command WHERE requested_at >= ?", cutoff);
        jdbc.update("DELETE FROM alarm WHERE raised_at >= ?", cutoff);
        jdbc.update("DELETE FROM sensor_event WHERE started_at >= ? OR ended_at IS NULL", cutoff);
        jdbc.update("DELETE FROM sensor_trend WHERE ts >= ?", cutoff);
        daily.resetFromSimStart();
        jdbc.update("UPDATE equipment SET run_state = 'RUN', "
                + "fan_rpm = CASE WHEN equipment_type = 'FAN' THEN 1200 ELSE fan_rpm END");
        engine = null;
        listener = null;
        state.clear();
        clock.restart();
        feed.append("RESET", clock.nowOffset(), Map.of());
        log.info("시연 리셋: 시계 {} 부터 다시 시작", clock.nowOffset());
    }

    /** 센서·기준값을 바꾼 뒤 엔진에 반영한다. 진행 중 판정·경보는 그대로 이어진다 */
    public synchronized void reloadDefinitions() {
        // 엣지로 기준값 다시 보내기 (Kafka 켜짐일 때 ThresholdPublisher 가 커밋 뒤에 받음)
        events.publishEvent(new DefinitionsChanged());
        if (engine == null) {
            return; // 다음 값이 들어올 때 새 기준으로 만들어진다
        }
        List<SensorDef> sensors = sensorLoader.loadActive();
        Map<String, Long> ids = new HashMap<>();
        for (SensorDef s : sensors) {
            ids.put(s.tag(), s.sensorId());
        }
        sensorIdByTag = ids;
        engine.updateDefinitions(sensors);
    }

    /** 센서·기준값이 바뀜 */
    public record DefinitionsChanged() {
    }

    private JudgementEngine engine() {
        if (engine == null) {
            List<SensorDef> sensors = sensorLoader.loadActive();
            Map<String, Long> ids = new HashMap<>();
            for (SensorDef s : sensors) {
                ids.put(s.tag(), s.sensorId());
            }
            sensorIdByTag = ids;
            listener = new FeedingListener(new JdbcJudgementStore(jdbc, clock.zone(), 1), feed, clock.zone());
            engine = new JudgementEngine(sensors, listener, JudgementEngine.Options.live());
        }
        return engine;
    }

    private OffsetDateTime odt(java.time.Instant i) {
        return i.atZone(clock.zone()).toOffsetDateTime();
    }
}
