package com.gte.argos.gateway.stats;

import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.central.CentralDto.Alarm;
import com.gte.argos.gateway.central.CentralDto.Event;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 통계·이력 화면의 조건 줄 (API 명세서 D1 공통 파라미터).
 *
 * @param from       시작 날짜 (그날 0시부터)
 * @param to         끝 날짜 (그날 24시까지)
 * @param factoryId  공장
 * @param processId  공정
 * @param sensorType CURRENT | TEMP | CO | IR (대표 이벤트의 센서)
 * @param level      WARN | ALARM (정확히 그 단계)
 * @param shift      DAY(07~19시) | NIGHT(19~07시), 발생 시각 기준
 * @param hazard     FIRE | GAS (alarm.hazard). 비우면 전체 (2026-11-19 통계·이력 개편)
 * @param status     ACTIVE(해제 전 전부) | CLOSED | OPEN · ACKED · ACTING. 비우면 전체
 * @param siteId     사업장
 */
public record AlarmCriteria(LocalDate from, LocalDate to, Long factoryId, Long processId, String sensorType,
                            String level, String shift, String hazard, String status, Long siteId) {

    public AlarmCriteria(LocalDate from, LocalDate to, Long factoryId, Long processId, String sensorType,
                         String level, String shift) {
        this(from, to, factoryId, processId, sensorType, level, shift, null, null, null);
    }

    public AlarmCriteria {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("기간을 확인하세요");
        }
    }

    public OffsetDateTime start() {
        return from.atStartOfDay(SimTime.ZONE).toOffsetDateTime();
    }

    public OffsetDateTime end() {
        return to.plusDays(1).atStartOfDay(SimTime.ZONE).toOffsetDateTime();
    }

    public long days() {
        return Duration.between(start(), end()).toDays();
    }

    /** 바로 앞의 같은 길이 기간 (직전 기간 대비 변화용) */
    public AlarmCriteria previous() {
        long d = days();
        return new AlarmCriteria(from.minusDays(d), from.minusDays(1), factoryId, processId, sensorType, level, shift,
                hazard, status, siteId);
    }

    public boolean matches(Alarm a, UserScope scope, MasterCache.Snapshot m) {
        if (!scope.canProcess(a.processId(), m)) {
            return false;
        }
        if (factoryId != null && a.factoryId() != factoryId) {
            return false;
        }
        if (processId != null && a.processId() != processId) {
            return false;
        }
        if (siteId != null && a.plantId() != siteId) {
            return false;
        }
        if (hazard != null && !hazard.isBlank() && !hazard.equals(a.hazard())) {
            return false;
        }
        if (status != null && !status.isBlank() && !statusMatches(status, a.status())) {
            return false;
        }
        if (level != null && !level.isBlank()) {
            Integer st = Codes.stage(level);
            if (st != null && a.stage() != st) {
                return false;
            }
        }
        if (sensorType != null && !sensorType.isBlank()) {
            Event e = a.mainEvent();
            if (e == null || !sensorType.equals(Codes.sensorType(e.type()))) {
                return false;
            }
        }
        if (shift != null && !shift.isBlank()) {
            boolean day = isDayShift(a.raisedAt());
            if ("DAY".equals(shift) != day) {
                return false;
            }
        }
        return true;
    }

    /** ACTIVE = 해제 전(OPEN·ACKED·ACTING), 그 밖은 같은 값 */
    static boolean statusMatches(String want, String actual) {
        if ("ACTIVE".equals(want)) {
            return !"CLOSED".equals(actual);
        }
        return want.equals(actual);
    }

    /** 주간 07:00~18:59, 나머지는 야간 */
    public static boolean isDayShift(OffsetDateTime t) {
        int h = t.atZoneSameInstant(SimTime.ZONE).getHour();
        return h >= 7 && h < 19;
    }
}
