package com.gte.argos.factory.query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 연계 서버에 내주는 응답 모양 모음.
 * 단계(stage)는 DB 와 같은 숫자 1=정상, 2=주의, 3=경보. 화면용 문자(NORMAL/WARN/ALARM)로 바꾸는 것은 연계 서버 몫.
 * 센서 종류도 DB 값(TE/IT/AT/IR) 그대로 준다.
 */
public final class InternalDto {

    private InternalDto() {
    }

    // ---------------------------------------------------------------
    // 마스터
    // ---------------------------------------------------------------

    public record Master(
            List<Plant> plants,
            List<Factory> factories,
            List<Process> processes,
            List<Equipment> equipment,
            List<Sensor> sensors,
            List<Unit> units) {
    }

    public record Plant(long id, String code, String name, String address, Double lat, Double lng) {
    }

    /** mapShape: 사업장 지도 위 구역 좌표 (jsonb 원문 문자열) */
    public record Factory(long id, long plantId, String code, String name, int sortOrder, String mapShape) {
    }

    public record Process(long id, long factoryId, String code, String name, String tagPrefix, Long floorplanFileId) {
    }

    public record Equipment(long id, long processId, String code, String name, String type, String shape,
                            Double posX, Double posY, Double posZ, Double sizeW, Double sizeD, Double sizeH,
                            boolean active, String runState, Integer fanRpm) {
    }

    public record Sensor(long id, String tag, long equipmentId, String type, String unit, String connection,
                         int sampleSec, Double setpoint, boolean active, Threshold threshold) {
    }

    /** 지금 적용 중인 기준값 */
    public record Threshold(long id, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                            int warnSamples, int alarmSamples, String season, String basis, String note,
                            OffsetDateTime validFrom) {
    }

    public record Unit(long id, long processId, Long equipmentId, String name, boolean active, List<UnitMember> members) {
    }

    public record UnitMember(long sensorId, String hazard) {
    }

    // ---------------------------------------------------------------
    // 현재 상태
    // ---------------------------------------------------------------

    /**
     * @param at        시연 시계의 지금
     * @param sensors   센서별 지금 값·단계
     * @param equipment 설비별 단계·운전 상태
     * @param levels    범위별 단계 (plants / factories / processes / units → id → stage)
     */
    public record Status(OffsetDateTime at, List<SensorStatus> sensors, List<EquipmentStatus> equipment,
                         Map<String, Map<Long, Integer>> levels) {
    }

    /**
     * @param value   지금 값 (실시간 값이 없으면 마지막 1분 평균)
     * @param valueAt 그 값의 시각
     * @param live    true = 실시간 5초 값, false = 1분 추이에서 가져온 값
     * @param stage   진행 중 이벤트의 단계, 없으면 1
     */
    public record SensorStatus(long sensorId, String tag, String type, String unit, long equipmentId, long processId,
                               Double value, OffsetDateTime valueAt, boolean live, int stage,
                               Double warn, Double alarm, Double lowWarn, Double lowAlarm) {
    }

    public record EquipmentStatus(long equipmentId, long processId, String runState, Integer fanRpm, int stage) {
    }

    // ---------------------------------------------------------------
    // 추이
    // ---------------------------------------------------------------

    /** @param step 5s | 1m | 10m */
    public record Series(String step, List<SensorSeries> series) {
    }

    /** points: [시각, 평균, 최소, 최대] (5s 는 최소=최대=값) */
    public record SensorSeries(long sensorId, String tag, String unit, Double warn, Double alarm,
                               Double lowWarn, Double lowAlarm, List<Object[]> points) {
    }

    // ---------------------------------------------------------------
    // 이벤트 · 경보
    // ---------------------------------------------------------------

    public record Event(long eventId, long sensorId, String tag, String type, String unit, long equipmentId,
                        long processId, int stage, String rule, OffsetDateTime startedAt, OffsetDateTime endedAt,
                        Double peak, Long thresholdId, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                        int maxStage, String edgeKey) {
    }

    public record Sample(OffsetDateTime ts, Double value) {
    }

    /**
     * @param closeReason    종료 사유 AUTO_RECOVERED | ACTION_DONE | MANUAL (열려 있으면 null)
     * @param valueStillOver 아직 끝나지 않은 이벤트가 있음 (경보 종료 불가)
     */
    public record Alarm(long alarmId, long unitId, String unitName, long processId, Long equipmentId,
                        long factoryId, long plantId, int stage, String hazard, String status,
                        OffsetDateTime raisedAt, Long ackedBy, OffsetDateTime ackedAt,
                        Long closedBy, OffsetDateTime closedAt, Long thermalFileId, String closeReason,
                        boolean valueStillOver, List<Event> events) {
    }

    public record Page<T>(List<T> items, int page, int size, long total) {
    }
}
