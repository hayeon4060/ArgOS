package com.gte.argos.factory.judge;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * 센서 이벤트를 관제 단위 경보로 묶는다.
 *
 * <pre>
 * - 관제 단위마다 열린 경보는 하나. 그 단위 센서에서 이벤트가 생기면 그 경보에 붙인다.
 * - 경보 단계 = 붙은 이벤트 중 가장 높은 단계
 * - 사고 종류(hazard):
 *     온도(TE)·열화상(IR) 이벤트가 있으면 FIRE
 *     없고 CO(AT) 이벤트가 있으면 GAS
 *     전류(IT)만 있으면 과전류(상한) = FIRE(과열), 전류 저하(하한) = GAS(배기 약화)
 *   단, 그 단위에서 센서에 정해 둔 hazard 에 없는 값이면 정해 둔 값을 쓴다.
 *   한 번 FIRE 가 된 경보는 GAS 로 내려가지 않는다.
 * - 붙은 이벤트가 모두 끝나면 onAlarmCleared. autoClose 면 경보도 닫고, 아니면 사람이 닫을 때까지 열어 둔다.
 * </pre>
 */
final class AlarmCorrelator {

    private final JudgementListener listener;
    private final LongSupplier nextAlarmKey;
    private final boolean autoClose;

    /** unitId → 열린 경보 */
    private final Map<Long, OpenAlarm> openByUnit = new HashMap<>();

    AlarmCorrelator(JudgementListener listener, LongSupplier nextAlarmKey, boolean autoClose) {
        this.listener = listener;
        this.nextAlarmKey = nextAlarmKey;
        this.autoClose = autoClose;
    }

    void onEventOpened(SensorDef sensor, long eventKey, Stage stage, boolean lowSide, Instant detectedAt) {
        for (SensorDef.UnitMembership unit : sensor.units()) {
            OpenAlarm alarm = openByUnit.get(unit.unitId());
            EventRef ref = new EventRef(eventKey, sensor.type(), lowSide, unit.hazards(), stage);
            if (alarm == null) {
                alarm = new OpenAlarm(nextAlarmKey.getAsLong(), unit.unitId());
                alarm.active.put(eventKey, ref);
                alarm.linked.add(eventKey);
                alarm.stage = stage;
                alarm.hazard = alarm.decideHazard();
                openByUnit.put(unit.unitId(), alarm);
                listener.onAlarmOpened(alarm.key, unit.unitId(), alarm.stage, alarm.hazard, detectedAt);
                listener.onAlarmEventLinked(alarm.key, eventKey);
                continue;
            }
            alarm.active.put(eventKey, ref);
            alarm.linked.add(eventKey);
            listener.onAlarmEventLinked(alarm.key, eventKey);
            alarm.refresh(listener, detectedAt);
        }
    }

    void onEventEscalated(SensorDef sensor, long eventKey, Stage stage, Instant at) {
        for (SensorDef.UnitMembership unit : sensor.units()) {
            OpenAlarm alarm = openByUnit.get(unit.unitId());
            if (alarm == null) {
                continue;
            }
            EventRef ref = alarm.active.get(eventKey);
            if (ref != null) {
                alarm.active.put(eventKey, ref.withStage(stage));
                alarm.refresh(listener, at);
            }
        }
    }

    void onEventClosed(SensorDef sensor, long eventKey, Instant endedAt) {
        for (SensorDef.UnitMembership unit : sensor.units()) {
            OpenAlarm alarm = openByUnit.get(unit.unitId());
            if (alarm == null || alarm.active.remove(eventKey) == null) {
                continue;
            }
            if (alarm.active.isEmpty()) {
                listener.onAlarmCleared(alarm.key, endedAt, autoClose);
                if (autoClose) {
                    openByUnit.remove(unit.unitId());
                }
            }
        }
    }

    /**
     * 서버를 다시 켤 때 DB 에 열린 경보를 이어 받는다. events: 이 경보에 묶인 진행 중 이벤트
     * (센서, 엔진 번호, 단계, 하한 여부)
     */
    void restore(long alarmKey, long unitId, Stage stage, Hazard hazard,
                 java.util.List<RestoredRef> events) {
        OpenAlarm alarm = new OpenAlarm(alarmKey, unitId);
        alarm.stage = stage;
        alarm.hazard = hazard;
        for (RestoredRef r : events) {
            Set<Hazard> allowed = Set.of();
            for (SensorDef.UnitMembership u : r.sensor().units()) {
                if (u.unitId() == unitId) {
                    allowed = u.hazards();
                }
            }
            alarm.active.put(r.eventKey(), new EventRef(r.eventKey(), r.sensor().type(), r.lowSide(), allowed, r.stage()));
            alarm.linked.add(r.eventKey());
        }
        openByUnit.put(unitId, alarm);
    }

    record RestoredRef(SensorDef sensor, long eventKey, Stage stage, boolean lowSide) {
    }

    /** 사람이 경보를 닫았을 때 (실시간). 이후 그 단위의 새 이벤트는 새 경보가 된다 */
    void closeAlarm(long unitId) {
        openByUnit.remove(unitId);
    }

    /** 이 단위에 열린 경보의 엔진 번호, 없으면 -1 */
    long openAlarmKey(long unitId) {
        OpenAlarm alarm = openByUnit.get(unitId);
        return alarm == null ? -1 : alarm.key;
    }

    private record EventRef(long eventKey, String sensorType, boolean lowSide, Set<Hazard> allowed, Stage stage) {
        EventRef withStage(Stage s) {
            return new EventRef(eventKey, sensorType, lowSide, allowed, s);
        }

        Hazard hazard() {
            Hazard h = switch (sensorType) {
                case "TE", "IR" -> Hazard.FIRE;
                case "AT" -> Hazard.GAS;
                default -> lowSide ? Hazard.GAS : Hazard.FIRE;
            };
            if (!allowed.isEmpty() && !allowed.contains(h)) {
                return allowed.iterator().next();
            }
            return h;
        }
    }

    private static final class OpenAlarm {
        final long key;
        final long unitId;
        final Map<Long, EventRef> active = new HashMap<>();
        final Set<Long> linked = new LinkedHashSet<>();
        Stage stage;
        Hazard hazard;

        OpenAlarm(long key, long unitId) {
            this.key = key;
            this.unitId = unitId;
        }

        /** 열려 있는 이벤트로 사고 종류를 정한다. 한 번 FIRE 가 되면 FIRE 로 유지한다 */
        Hazard decideHazard() {
            if (hazard == Hazard.FIRE) {
                return Hazard.FIRE;
            }
            boolean fireBySensor = false;
            boolean gasBySensor = false;
            Hazard currentOnly = null;
            for (EventRef ref : active.values()) {
                if ("TE".equals(ref.sensorType()) || "IR".equals(ref.sensorType())) {
                    fireBySensor = true;
                } else if ("AT".equals(ref.sensorType())) {
                    gasBySensor = true;
                } else if (currentOnly == null) {
                    currentOnly = ref.hazard();
                }
            }
            if (fireBySensor) {
                return Hazard.FIRE;
            }
            if (gasBySensor) {
                return Hazard.GAS;
            }
            if (currentOnly != null) {
                return currentOnly;
            }
            return hazard == null ? Hazard.FIRE : hazard;
        }

        void refresh(JudgementListener listener, Instant at) {
            Stage newStage = stage;
            for (EventRef ref : active.values()) {
                newStage = Stage.max(newStage, ref.stage());
            }
            Hazard newHazard = decideHazard();
            // 경보 단계는 내려가지 않는다 (한 번 경보면 사람이 닫을 때까지 경보)
            if (newStage != stage || newHazard != hazard) {
                stage = newStage;
                hazard = newHazard;
                listener.onAlarmUpdated(key, stage, hazard, at);
            }
        }
    }
}
