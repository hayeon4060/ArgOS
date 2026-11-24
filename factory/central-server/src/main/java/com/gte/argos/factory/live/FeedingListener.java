package com.gte.argos.factory.live;

import com.gte.argos.factory.judge.Hazard;
import com.gte.argos.factory.judge.JudgementListener;
import com.gte.argos.factory.judge.Stage;
import com.gte.argos.factory.store.JdbcJudgementStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실시간 판정 결과를 DB 에 저장(store)한 뒤, 같은 내용을 피드에 남긴다.
 * 피드에는 DB id(eventId, alarmId)를 넣으므로 반드시 저장 다음에 기록한다.
 */
public class FeedingListener implements JudgementListener {

    private final JdbcJudgementStore store;
    private final LiveFeed feed;
    private final ZoneId zone;
    private final Map<Long, Long> sensorByEventKey = new HashMap<>();
    private final Map<Long, Long> unitByAlarmKey = new HashMap<>();
    private final Map<Long, Integer> gasStageByAlarmKey = new HashMap<>();
    private final List<GasAlarm> gasAlarms = new ArrayList<>();
    /** 이번 판정에서 값이 돌아와 자동으로 닫힌 경보 id */
    private final List<Long> closedAlarms = new ArrayList<>();

    public FeedingListener(JdbcJudgementStore store, LiveFeed feed, ZoneId zone) {
        this.store = store;
        this.feed = feed;
        this.zone = zone;
    }

    @Override
    public void onReading(long sensorId, Instant ts, double value) {
        store.onReading(sensorId, ts, value);
    }

    @Override
    public void onTrend(TrendPoint point) {
        store.onTrend(point);
    }

    @Override
    public void onEventOpened(long eventKey, long sensorId, long thresholdId, Stage stage, Instant startedAt,
                              Instant detectedAt, boolean lowSide, String rule, List<Sample> backlog) {
        store.onEventOpened(eventKey, sensorId, thresholdId, stage, startedAt, detectedAt, lowSide, rule, backlog);
        sensorByEventKey.put(eventKey, sensorId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", store.eventId(eventKey));
        data.put("sensorId", sensorId);
        data.put("stage", stage.code());
        data.put("startedAt", odt(startedAt));
        data.put("lowSide", lowSide);
        data.put("rule", rule);
        feed.append("EVENT_OPENED", odt(detectedAt), data);
    }

    @Override
    public void onEventSample(long eventKey, Sample sample) {
        store.onEventSample(eventKey, sample);
    }

    @Override
    public void onEventEscalated(long eventKey, Stage stage, Instant at) {
        store.onEventEscalated(eventKey, stage, at);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", store.eventId(eventKey));
        data.put("sensorId", sensorByEventKey.get(eventKey));
        data.put("stage", stage.code());
        feed.append("EVENT_ESCALATED", odt(at), data);
    }

    @Override
    public void onEventClosed(long eventKey, Instant endedAt, double peak) {
        store.onEventClosed(eventKey, endedAt, peak);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventId", store.eventId(eventKey));
        data.put("sensorId", sensorByEventKey.remove(eventKey));
        data.put("peak", peak);
        feed.append("EVENT_CLOSED", odt(endedAt), data);
    }

    @Override
    public void onEdgeEvent(long eventKey, String edgeKey, Boolean plcOk) {
        store.onEdgeEvent(eventKey, edgeKey, plcOk);
    }

    @Override
    public void onEventStageLowered(long eventKey, Stage stage, Instant at) {
        store.onEventStageLowered(eventKey, stage, at);
    }

    @Override
    public void onEdgeEventEndedUnknown(String edgeKey, Instant endedAt, Double peak) {
        store.onEdgeEventEndedUnknown(edgeKey, endedAt, peak);
    }

    @Override
    public void onAlarmOpened(long alarmKey, long unitId, Stage stage, Hazard hazard, Instant raisedAt) {
        store.onAlarmOpened(alarmKey, unitId, stage, hazard, raisedAt);
        unitByAlarmKey.put(alarmKey, unitId);
        gasChanged(alarmKey, unitId, stage, hazard);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alarmId", store.alarmId(alarmKey));
        data.put("unitId", unitId);
        data.put("stage", stage.code());
        data.put("hazard", hazard.name());
        feed.append("ALARM_OPENED", odt(raisedAt), data);
    }

    @Override
    public void onAlarmEventLinked(long alarmKey, long eventKey) {
        store.onAlarmEventLinked(alarmKey, eventKey);
    }

    @Override
    public void onAlarmDetailsChanged(long alarmKey, Instant at) {
        Long alarmId = store.alarmId(alarmKey);
        if (alarmId != null) {
            feed.append("ALARM_DETAILS_CHANGED", odt(at), Map.of("alarmId", alarmId));
        }
    }

    @Override
    public void onAlarmUpdated(long alarmKey, Stage stage, Hazard hazard, Instant at) {
        store.onAlarmUpdated(alarmKey, stage, hazard, at);
        gasChanged(alarmKey, unitByAlarmKey.get(alarmKey), stage, hazard);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alarmId", store.alarmId(alarmKey));
        data.put("unitId", unitByAlarmKey.get(alarmKey));
        data.put("stage", stage.code());
        data.put("hazard", hazard.name());
        feed.append("ALARM_UPDATED", odt(at), data);
    }

    @Override
    public void onAlarmCleared(long alarmKey, Instant at, boolean closed) {
        store.onAlarmCleared(alarmKey, at, closed);
        if (closed && store.alarmId(alarmKey) != null) {
            closedAlarms.add(store.alarmId(alarmKey));
            gasStageByAlarmKey.remove(alarmKey);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alarmId", store.alarmId(alarmKey));
        data.put("unitId", unitByAlarmKey.get(alarmKey));
        data.put("closed", closed);
        feed.append("ALARM_CLEARED", odt(at), data);
    }

    public void flush() {
        store.flush();
    }

    /** 서버를 다시 켤 때 열린 이벤트를 이어 받음 */
    public void restoreEvent(long eventKey, long eventId, long sensorId) {
        store.restoreEvent(eventKey, eventId);
        sensorByEventKey.put(eventKey, sensorId);
    }

    /** 서버를 다시 켤 때 열린 경보를 이어 받음 (가스면 지금 단계를 기억해 배기팬 명령을 다시 보내지 않게) */
    public void restoreAlarm(long alarmKey, long alarmId, long unitId, Stage stage, Hazard hazard) {
        store.restoreAlarm(alarmKey, alarmId, unitId);
        unitByAlarmKey.put(alarmKey, unitId);
        if (hazard == Hazard.GAS) {
            gasStageByAlarmKey.put(alarmKey, stage.code());
        }
    }

    /** 사람이 경보를 닫음: 그 단위 알림의 센서 값 저장을 멈춘다 */
    public void alarmClosed(long unitId) {
        store.forgetUnit(unitId);
    }

    /** 이번 판정에서 자동으로 닫힌 경보. 꺼내면 비워진다 */
    public List<Long> drainClosedAlarms() {
        List<Long> out = List.copyOf(closedAlarms);
        closedAlarms.clear();
        return out;
    }

    /** 이번 ingest 에서 새로 생기거나 단계가 올라간 가스 알림 (배기팬 자동 제어용). 꺼내면 비워진다 */
    public List<GasAlarm> drainGasAlarms() {
        List<GasAlarm> out = List.copyOf(gasAlarms);
        gasAlarms.clear();
        return out;
    }

    private void gasChanged(long alarmKey, Long unitId, Stage stage, Hazard hazard) {
        if (hazard != Hazard.GAS || unitId == null) {
            return;
        }
        Integer before = gasStageByAlarmKey.get(alarmKey);
        if (before == null || stage.code() > before) {
            gasStageByAlarmKey.put(alarmKey, stage.code());
            Long alarmId = store.alarmId(alarmKey);
            if (alarmId != null) {
                gasAlarms.add(new GasAlarm(alarmId, unitId, stage.code()));
            }
        }
    }

    public record GasAlarm(long alarmId, long unitId, int stage) {
    }

    private OffsetDateTime odt(Instant i) {
        return i.atZone(zone).toOffsetDateTime();
    }
}
