package com.gte.argos.gateway.central;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 중앙 서버 연계 API(/api/v1/internal/**) 응답 모양. 중앙 서버 InternalDto 와 같아야 한다.
 * 단계는 숫자(1 정상, 2 주의, 3 경보), 센서 종류는 DB 값(TE/IT/AT/IR) 그대로 온다. 화면용 변환은 Codes.
 */
public final class CentralDto {

    private CentralDto() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope<T>(boolean success, T data, Map<String, Object> error) {
    }

    // 마스터 ------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Master(List<Plant> plants, List<Factory> factories, List<Process> processes,
                         List<Equipment> equipment, List<Sensor> sensors, List<Unit> units) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Plant(long id, String code, String name, String address, Double lat, Double lng) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Factory(long id, long plantId, String code, String name, int sortOrder, String mapShape) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Process(long id, long factoryId, String code, String name, String tagPrefix, Long floorplanFileId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Equipment(long id, long processId, String code, String name, String type, String shape,
                            Double posX, Double posY, Double posZ, Double sizeW, Double sizeD, Double sizeH,
                            boolean active, String runState, Integer fanRpm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sensor(long id, String tag, long equipmentId, String type, String unit, String connection,
                         int sampleSec, Double setpoint, boolean active, Threshold threshold) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Threshold(long id, Double warn, Double alarm, Double lowWarn, Double lowAlarm,
                            int warnSamples, int alarmSamples, String season, String basis, String note,
                            OffsetDateTime validFrom) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Unit(long id, long processId, Long equipmentId, String name, boolean active, List<UnitMember> members) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UnitMember(long sensorId, String hazard) {
    }

    // 현재 상태 ---------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(OffsetDateTime at, List<SensorStatus> sensors, List<EquipmentStatus> equipment,
                         Map<String, Map<Long, Integer>> levels) {

        public int level(String scope, long id) {
            Map<Long, Integer> m = levels == null ? null : levels.get(scope);
            Integer v = m == null ? null : m.get(id);
            return v == null ? 1 : v;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SensorStatus(long sensorId, String tag, String type, String unit, long equipmentId, long processId,
                               Double value, OffsetDateTime valueAt, boolean live, int stage,
                               Double warn, Double alarm, Double lowWarn, Double lowAlarm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EquipmentStatus(long equipmentId, long processId, String runState, Integer fanRpm, int stage) {
    }

    // 추이 · 이벤트 · 경보 ----------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Series(String step, List<SensorSeries> series) {
    }

    /** points: [시각, 평균, 최소, 최대] */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SensorSeries(long sensorId, String tag, String unit, Double warn, Double alarm,
                               Double lowWarn, Double lowAlarm, List<List<Object>> points) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(long eventId, long sensorId, String tag, String type, String unit, long equipmentId,
                        long processId, int stage, String rule, OffsetDateTime startedAt, OffsetDateTime endedAt,
                        Double peak, Long thresholdId, Double warn, Double alarm, Double lowWarn, Double lowAlarm) {

        /** 하한 쪽(전류 저하 등)으로 벗어난 이벤트인지 */
        public boolean lowSide() {
            return peak != null && lowWarn != null && peak <= lowWarn && (warn == null || peak < warn);
        }

        /** 이 이벤트 단계의 기준값 */
        public Double threshold() {
            if (lowSide()) {
                return stage >= 3 ? lowAlarm : lowWarn;
            }
            return stage >= 3 ? alarm : warn;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sample(OffsetDateTime ts, Double value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alarm(long alarmId, long unitId, String unitName, long processId, Long equipmentId,
                        long factoryId, long plantId, int stage, String hazard, String status,
                        OffsetDateTime raisedAt, Long ackedBy, OffsetDateTime ackedAt,
                        Long closedBy, OffsetDateTime closedAt, Long thermalFileId,
                        boolean valueStillOver, List<Event> events) {

        /** 화면에 대표로 보여줄 이벤트: 단계가 가장 높고, 같으면 먼저 시작한 것 */
        public Event mainEvent() {
            Event best = null;
            if (events == null) {
                return null;
            }
            for (Event e : events) {
                if (best == null || e.stage() > best.stage()) {
                    best = e;
                }
            }
            return best;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page<T>(List<T> items, int page, int size, long total) {
    }

    // 피드 · 명령 · 시계 ------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeedItem(long seq, String type, OffsetDateTime at, Map<String, Object> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeedPage(String bootId, List<FeedItem> items, long next, long latest, boolean gap) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommandResult(long commandId, long equipmentId, String command, String status, String runState,
                                Integer fanRpm, Double value, String trigger) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clock(OffsetDateTime now, OffsetDateTime simStart, String zone) {
    }
}
