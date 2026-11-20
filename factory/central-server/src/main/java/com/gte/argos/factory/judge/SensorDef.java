package com.gte.argos.factory.judge;

import java.util.List;
import java.util.Objects;

/**
 * 판정 엔진이 알아야 하는 센서 정보.
 *
 * @param sensorId  sensor.sensor_id
 * @param tag       sensor.tag
 * @param type      sensor.sensor_type (TE / IT / AT / IR)
 * @param sampleSec 수집 주기(초). 이 주기의 3배 넘게 값이 비면 연속 카운트를 끊는다
 * @param rule      지금 적용 중인 기준값 (없으면 단계 판정 없이 추이만 쌓음)
 * @param units     이 센서가 속한 관제 단위들
 */
public record SensorDef(
        long sensorId,
        String tag,
        String type,
        int sampleSec,
        ThresholdRule rule,
        List<UnitMembership> units) {

    public SensorDef {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(type, "type");
        units = units == null ? List.of() : List.copyOf(units);
    }

    /**
     * 관제 단위 소속
     *
     * @param unitId  control_unit.unit_id
     * @param hazards 이 단위에서 이 센서가 쓰이는 사고 종류
     */
    public record UnitMembership(long unitId, java.util.Set<Hazard> hazards) {
        public UnitMembership {
            hazards = java.util.Set.copyOf(hazards);
        }
    }
}
