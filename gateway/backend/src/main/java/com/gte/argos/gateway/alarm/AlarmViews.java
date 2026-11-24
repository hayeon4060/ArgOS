package com.gte.argos.gateway.alarm;

import com.gte.argos.gateway.central.CentralDto;
import com.gte.argos.gateway.central.CentralDto.Alarm;
import com.gte.argos.gateway.central.CentralDto.Event;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.checklist.ChecklistRepository.Checklist;
import java.util.LinkedHashMap;
import java.util.Map;

/** 중앙 경보 → 화면 모양 (API 명세서 B1-2 알림 목록, SSE alarm.created) */
public final class AlarmViews {

    private AlarmViews() {
    }

    /** "전류 주의 기준 초과", 전류 저하는 "전류 주의 기준 미달" */
    public static String title(Alarm a) {
        Event e = a.mainEvent();
        if (e == null) {
            return Codes.stageName(a.stage()) + " 발생";
        }
        return Codes.sensorName(e.type()) + " " + Codes.stageName(a.stage()) + " 기준 " + (e.lowSide() ? "미달" : "초과");
    }

    /** 경보에 연결된 이벤트가 전류 이상뿐이면 화재·가스 양쪽 가능성을 표시한다. */
    public static String hazardLabel(Alarm alarm) {
        if (alarm.events() != null && !alarm.events().isEmpty()
                && alarm.events().stream().allMatch(event -> "IT".equals(event.type()))) {
            return "화재 또는 가스";
        }
        if ("FIRE".equals(alarm.hazard())) {
            return "화재";
        }
        if ("GAS".equals(alarm.hazard())) {
            return "가스";
        }
        return "";
    }

    /** "5공장 · 도장 공정 · 상도 건조로 배기팬" */
    public static String location(Alarm a, MasterCache.Snapshot m) {
        Event e = a.mainEvent();
        Long equipmentId = e != null ? Long.valueOf(e.equipmentId()) : a.equipmentId();
        return m.location(a.processId(), equipmentId);
    }

    /** 가스 알림의 배기팬 자동 제어 (fan-auto-api.md 2장). 가스가 아니거나 배기팬이 없으면 null */
    public static Map<String, Object> fanAuto(Alarm a) {
        CentralDto.FanAuto f = a.fanAuto();
        if (f == null || !"GAS".equals(a.hazard())) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("equipmentId", f.equipmentId());
        m.put("fanEquipmentId", f.fanEquipmentId());
        m.put("fanName", f.fanName());
        m.put("mode", f.mode());
        m.put("percent", f.percent());
        m.put("rpm", f.rpm());
        m.put("beforeRpm", f.beforeRpm());
        m.put("status", f.status());
        m.put("at", f.at());
        m.put("commandId", f.commandId());
        return m;
    }

    /** 알림 목록 한 줄 */
    public static Map<String, Object> item(Alarm a, MasterCache.Snapshot m, Map<Long, String> userNames,
                                           Checklist checklist) {
        Event e = a.mainEvent();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.alarmId());
        row.put("level", Codes.level(a.stage()));
        row.put("status", a.status());
        row.put("hazard", a.hazard());
        row.put("hazardLabel", hazardLabel(a));
        row.put("title", title(a));
        row.put("location", location(a, m));
        row.put("unitId", a.unitId());
        row.put("unitName", a.unitName());
        row.put("siteId", a.plantId());
        row.put("factoryId", a.factoryId());
        row.put("processId", a.processId());
        row.put("equipmentId", e != null ? e.equipmentId() : a.equipmentId());
        row.put("sensorId", e == null ? null : e.sensorId());
        row.put("sensorTag", e == null ? null : e.tag());
        row.put("sensorType", e == null ? null : Codes.sensorType(e.type()));
        row.put("value", e == null ? null : e.peak());
        row.put("unit", e == null ? null : e.unit());
        row.put("threshold", e == null ? null : e.threshold());
        row.put("raisedAt", a.raisedAt());
        row.put("ackedBy", a.ackedBy() == null ? null : userNames.get(a.ackedBy()));
        row.put("ackedAt", a.ackedAt());
        row.put("closedAt", a.closedAt());
        row.put("closeReason", a.closeReason());
        row.put("valueStillOver", a.valueStillOver());
        row.put("fanAuto", fanAuto(a));
        if (checklist != null) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", checklist.id());
            c.put("status", checklist.status());
            c.put("doneCount", checklist.doneCount());
            c.put("totalCount", checklist.items().size());
            row.put("checklist", c);
            row.put("assignee", checklist.assigneeName());
        } else {
            row.put("checklist", null);
            row.put("assignee", null);
        }
        if (a.closedAt() != null && a.raisedAt() != null) {
            row.put("actionMinutes", Math.max(1, java.time.Duration.between(a.raisedAt(), a.closedAt()).toMinutes()));
        } else {
            row.put("actionMinutes", null);
        }
        return row;
    }
}
