package com.gte.argos.factory.live;

import com.gte.argos.factory.judge.Hazard;
import com.gte.argos.factory.judge.JudgementListener;
import com.gte.argos.factory.judge.Stage;
import com.gte.argos.factory.store.JdbcJudgementStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    public FeedingListener(JdbcJudgementStore store, LiveFeed feed, ZoneId zone) {
        this.store = store;
        this.feed = feed;
        this.zone = zone;
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
    public void onAlarmOpened(long alarmKey, long unitId, Stage stage, Hazard hazard, Instant raisedAt) {
        store.onAlarmOpened(alarmKey, unitId, stage, hazard, raisedAt);
        unitByAlarmKey.put(alarmKey, unitId);
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
    public void onAlarmUpdated(long alarmKey, Stage stage, Hazard hazard, Instant at) {
        store.onAlarmUpdated(alarmKey, stage, hazard, at);
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
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alarmId", store.alarmId(alarmKey));
        data.put("unitId", unitByAlarmKey.get(alarmKey));
        data.put("closed", closed);
        feed.append("ALARM_CLEARED", odt(at), data);
    }

    public void flush() {
        store.flush();
    }

    private OffsetDateTime odt(Instant i) {
        return i.atZone(zone).toOffsetDateTime();
    }
}
