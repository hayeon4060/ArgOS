package com.gte.argos.factory.judge;

import java.time.Instant;
import java.util.List;

/**
 * 판정 엔진이 내보내는 결과를 받는 쪽.
 * 7일치 가져오기는 DB 에 일괄 저장하고, 실시간은 DB 저장 + 연계 서버 전달을 한다.
 * eventKey · alarmKey 는 엔진 안에서만 쓰는 번호이며, 받는 쪽이 DB id 와 짝지어 둔다.
 */
public interface JudgementListener {

    /** 1분 추이 한 칸이 끝남 */
    default void onTrend(TrendPoint point) {
    }

    /**
     * 센서 이벤트 시작 (지속 조건을 채운 순간)
     *
     * @param rule    판정 규칙 (sensor_event.rule): HI 상한 / LO 하한 / IMMEDIATE 즉시 경보
     * @param backlog 조건을 채우기까지 쌓인 샘플 (startedAt 부터 detectedAt 까지)
     */
    default void onEventOpened(long eventKey, long sensorId, long thresholdId, Stage stage,
                               Instant startedAt, Instant detectedAt, boolean lowSide, String rule,
                               List<Sample> backlog) {
    }

    /** 이벤트가 이어지는 동안 들어온 샘플 */
    default void onEventSample(long eventKey, Sample sample) {
    }

    /** 이벤트 단계가 올라감 (주의 → 경보) */
    default void onEventEscalated(long eventKey, Stage stage, Instant at) {
    }

    /** 이벤트 끝 (정상 값이 정해진 수만큼 이어짐). endedAt 은 정상으로 돌아온 첫 샘플 시각 */
    default void onEventClosed(long eventKey, Instant endedAt, double peak) {
    }

    /** 관제 단위에 경보가 새로 생김 */
    default void onAlarmOpened(long alarmKey, long unitId, Stage stage, Hazard hazard, Instant raisedAt) {
    }

    /** 경보와 원인 이벤트 연결 (alarm_event) */
    default void onAlarmEventLinked(long alarmKey, long eventKey) {
    }

    /** 경보 단계 또는 사고 종류가 바뀜 */
    default void onAlarmUpdated(long alarmKey, Stage stage, Hazard hazard, Instant at) {
    }

    /**
     * 경보에 묶인 이벤트가 모두 끝남.
     *
     * @param closed true 면 경보도 종료로 본다 (과거 데이터 가져오기). false 면 사람이 종료할 때까지 열어 둔다 (실시간)
     */
    default void onAlarmCleared(long alarmKey, Instant at, boolean closed) {
    }

    /** 5초 샘플 한 건 */
    record Sample(Instant ts, double value) {
    }

    /** 1분 추이 한 칸 (sensor_trend) */
    record TrendPoint(long sensorId, Instant minute, double avg, double min, double max, int n) {
    }
}
