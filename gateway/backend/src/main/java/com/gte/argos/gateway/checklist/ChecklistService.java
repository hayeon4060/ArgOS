package com.gte.argos.gateway.checklist;

import com.gte.argos.gateway.access.CurrentUser;
import com.gte.argos.gateway.access.UserScope;
import com.gte.argos.gateway.alarm.AlarmService;
import com.gte.argos.gateway.central.CentralClient;
import com.gte.argos.gateway.central.CentralDto.Alarm;
import com.gte.argos.gateway.central.CentralDto.Equipment;
import com.gte.argos.gateway.central.CentralDto.Event;
import com.gte.argos.gateway.central.Codes;
import com.gte.argos.gateway.central.MasterCache;
import com.gte.argos.gateway.central.SimTime;
import com.gte.argos.gateway.checklist.ChecklistRepository.Checklist;
import com.gte.argos.gateway.checklist.ChecklistRepository.Item;
import com.gte.argos.gateway.checklist.ChecklistRepository.Template;
import com.gte.argos.gateway.checklist.ChecklistRepository.Worker;
import com.gte.argos.gateway.common.ActivityLogger;
import com.gte.argos.gateway.common.api.ApiException;
import com.gte.argos.gateway.push.PushSender;
import com.gte.argos.gateway.stream.StreamHub;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 주의 대응 체크리스트 (API 명세서 C1 · C2 · M1 · M2 · I1-2).
 *
 * <pre>
 * 웹(공장 담당자)  POST /checklists        → SENT, 앱 푸시, 경보 ACTING
 * 앱(공정 담당자)  PATCH …/items/{itemId}  → IN_PROGRESS (첫 체크), SSE checklist.updated
 *                  POST …/report           → REPORTED
 * 웹              POST …/confirm           → CONFIRMED, 값이 기준 아래면 경보 CLOSED
 * </pre>
 * 시연: 완료 보고 → (봇에 정상 명령) → 값 회복 → 조치 확인 순서여야 경보가 닫힌다.
 */
@Service
public class ChecklistService {

    private static final Duration REMIND_COOLDOWN = Duration.ofSeconds(30);

    private final ChecklistRepository repo;
    private final CentralClient central;
    private final MasterCache masterCache;
    private final CurrentUser currentUser;
    private final AlarmService alarms;
    private final SimTime time;
    private final PushSender push;
    private final StreamHub hub;
    private final ActivityLogger activity;

    public ChecklistService(ChecklistRepository repo, CentralClient central, MasterCache masterCache,
                            CurrentUser currentUser, AlarmService alarms, SimTime time, PushSender push,
                            StreamHub hub, ActivityLogger activity) {
        this.repo = repo;
        this.central = central;
        this.masterCache = masterCache;
        this.currentUser = currentUser;
        this.alarms = alarms;
        this.time = time;
        this.push = push;
        this.hub = hub;
        this.activity = activity;
    }

    // ---------------------------------------------------------------
    // 웹 (공장 담당자)
    // ---------------------------------------------------------------

    /** GET /checklist-templates?equipmentType=FAN&sensorType=CURRENT */
    public Map<String, Object> template(String equipmentType, String sensorType) {
        currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        Template t = repo.template(equipmentType == null ? "" : equipmentType, Codes.dbSensorType(sensorType), 2);
        if (t == null) {
            throw ApiException.notFound("TEMPLATE_NOT_FOUND", "맞는 체크리스트 양식이 없습니다.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("templateId", t.id());
        out.put("cause", t.cause());
        out.put("items", t.items());
        return out;
    }

    /** GET /processes/{processId}/workers */
    public List<Map<String, Object>> workers(long processId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        currentUser.checkProcess(scope, processId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Worker w : repo.workers(processId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", w.id());
            row.put("name", w.name());
            row.put("loginId", w.loginId());
            row.put("onDuty", true); // 근무조 정보가 없어 모두 근무 중으로 본다
            row.put("pushReady", w.pushReady());
            out.add(row);
        }
        return out;
    }

    /** POST /checklists */
    public Map<String, Object> send(long alarmId, Long templateId, long assigneeId, List<String> items) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        Alarm a = alarms.visibleAlarm(alarmId);
        if ("CLOSED".equals(a.status())) {
            throw ApiException.conflict("ALARM_CLOSED", "이미 종료된 알림입니다.");
        }
        if (repo.activeForAlarm(alarmId) != null) {
            throw ApiException.conflict("CHECKLIST_EXISTS", "이 알림에 이미 진행 중인 체크리스트가 있습니다.");
        }
        if (items == null || items.stream().allMatch(s -> s == null || s.isBlank())) {
            throw new IllegalArgumentException("체크리스트 항목이 없습니다");
        }
        Worker worker = workerOf(a.processId(), assigneeId);
        OffsetDateTime now = time.now();
        List<String> clean = items.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
        long id = repo.create(alarmId, templateId, assigneeId, scope.userId(), now, clean);
        repo.addAction(alarmId, "CHECKLIST", worker.name(), "OK", clean.size() + "개 항목", scope.userId(), now);
        central.actingAlarm(alarmId, scope.userId());
        boolean pushed = push.send(repo.fcmToken(assigneeId), "CHECKLIST_ASSIGNED",
                Map.of("type", "CHECKLIST_ASSIGNED", "checklistId", String.valueOf(id)));
        repo.addAction(alarmId, "PUSH", worker.name(), pushed ? "OK" : "FAIL", "CHECKLIST_ASSIGNED", scope.userId(), now);
        publish(repo.find(id), null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", "SENT");
        out.put("push", pushed ? "SENT" : "FAILED");
        out.put("sentAt", now);
        return out;
    }

    /** GET /checklists?status=ACTIVE&factoryId= */
    public List<Map<String, Object>> list(String status, Long factoryId) {
        UserScope scope = currentUser.require(UserScope.HQ_ADMIN, UserScope.PLANT_ADMIN);
        MasterCache.Snapshot m = masterCache.get();
        List<String> statuses = status == null || "ACTIVE".equals(status) ? ChecklistRepository.ACTIVE : List.of(status);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Checklist c : repo.list(statuses, null, null)) {
            Alarm a = alarmOrNull(c.alarmId());
            if (a == null || !scope.canProcess(a.processId(), m) || (factoryId != null && a.factoryId() != factoryId)) {
                continue;
            }
            out.add(listRow(c, a, m));
        }
        return out;
    }

    /** POST /checklists/{id}/remind — 30초에 한 번 */
    public Map<String, Object> remind(long checklistId) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        Checklist c = visible(checklistId, scope);
        if (!"SENT".equals(c.status()) && !"IN_PROGRESS".equals(c.status())) {
            throw ApiException.conflict("CHECKLIST_CLOSED", "이미 완료 보고 또는 확인된 체크리스트입니다.");
        }
        OffsetDateTime now = time.now();
        if (c.lastRemindAt() != null && now.isBefore(c.lastRemindAt().plus(REMIND_COOLDOWN))) {
            OffsetDateTime at = c.lastRemindAt().plus(REMIND_COOLDOWN);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "REMIND_COOLDOWN", "잠시 뒤에 다시 보낼 수 있습니다.",
                    Map.of("availableAt", at));
        }
        repo.remind(checklistId, now);
        boolean pushed = push.send(repo.fcmToken(c.assigneeId()), "CHECKLIST_REMIND",
                Map.of("type", "CHECKLIST_REMIND", "checklistId", String.valueOf(checklistId)));
        repo.addAction(c.alarmId(), "PUSH", c.assigneeName(), pushed ? "OK" : "FAIL", "CHECKLIST_REMIND",
                scope.userId(), now);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("remindCount", c.remindCount() + 1);
        out.put("remindAvailableAt", now.plus(REMIND_COOLDOWN));
        return out;
    }

    /** PATCH /checklists/{id}/assignee */
    public Map<String, Object> reassign(long checklistId, long assigneeId, String reason) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        Checklist c = visible(checklistId, scope);
        if (!"SENT".equals(c.status()) && !"IN_PROGRESS".equals(c.status())) {
            throw ApiException.conflict("CHECKLIST_CLOSED", "이미 완료 보고 또는 확인된 체크리스트입니다.");
        }
        Alarm a = central.alarm(c.alarmId());
        Worker worker = workerOf(a.processId(), assigneeId);
        OffsetDateTime now = time.now();
        long before = c.assigneeId();
        repo.reassign(checklistId, assigneeId);
        repo.addAction(c.alarmId(), "REASSIGN", worker.name(), "OK", reason, scope.userId(), now);
        push.send(repo.fcmToken(assigneeId), "CHECKLIST_ASSIGNED",
                Map.of("type", "CHECKLIST_ASSIGNED", "checklistId", String.valueOf(checklistId)));
        push.send(repo.fcmToken(before), "CHECKLIST_REASSIGNED",
                Map.of("type", "CHECKLIST_REASSIGNED", "checklistId", String.valueOf(checklistId)));
        activity.log(scope.userId(), "CHECKLIST", a.plantId(), a.processId(), "checklist", checklistId,
                "체크리스트 담당자 변경 " + c.assigneeName() + " → " + worker.name()
                        + (reason == null || reason.isBlank() ? "" : " (" + reason + ")"));
        publish(repo.find(checklistId), null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", checklistId);
        out.put("assignee", Map.of("id", worker.id(), "name", worker.name()));
        return out;
    }

    /**
     * POST /checklists/{id}/confirm — 앱 완료 보고 뒤에만.
     * 체크리스트는 CONFIRMED 로 남기고, 값이 아직 기준 위면 경보는 열어 둔 채 409 VALUE_STILL_OVER.
     * (예외가 나도 체크리스트 확인은 지워지지 않도록 트랜잭션으로 묶지 않는다)
     *
     * @param result RESOLVED | PARTS_PLANNED | NEED_RECHECK
     */
    public Map<String, Object> confirm(long checklistId, String result, String memo) {
        UserScope scope = currentUser.require(UserScope.PLANT_ADMIN);
        Checklist c = visible(checklistId, scope);
        if ("CONFIRMED".equals(c.status())) {
            throw ApiException.conflict("CHECKLIST_CLOSED", "이미 확인된 체크리스트입니다.");
        }
        if (!"REPORTED".equals(c.status())) {
            throw ApiException.conflict("CHECKLIST_NOT_REPORTED", "앱에서 아직 완료 보고를 하지 않았습니다.");
        }
        OffsetDateTime now = time.now();
        repo.confirm(checklistId, scope.userId(), now);
        repo.addAction(c.alarmId(), "CONFIRM", null, "OK",
                (result == null ? "" : result) + (memo == null || memo.isBlank() ? "" : " / " + memo), scope.userId(), now);
        Checklist confirmed = repo.find(checklistId);
        publish(confirmed, null);

        Map<String, Object> checklist = new LinkedHashMap<>();
        checklist.put("id", checklistId);
        checklist.put("status", "CONFIRMED");
        checklist.put("confirmedAt", now);
        Alarm closed;
        try {
            closed = central.closeAlarm(c.alarmId(), scope.userId());
        } catch (ApiException e) {
            if ("VALUE_STILL_OVER".equals(e.code())) {
                Map<String, Object> details = new LinkedHashMap<>();
                if (e.details() != null) {
                    details.putAll(e.details());
                }
                details.put("checklist", checklist);
                throw new ApiException(HttpStatus.CONFLICT, "VALUE_STILL_OVER",
                        "체크리스트는 확인했지만 값이 아직 기준 위라 알림은 열려 있습니다.", details);
            }
            throw e;
        }
        repo.addAction(c.alarmId(), "CLOSE", null, "OK", "조치 확인으로 종료", scope.userId(), now);
        Map<String, Object> alarm = new LinkedHashMap<>();
        alarm.put("id", closed.alarmId());
        alarm.put("status", closed.status());
        alarm.put("closedAt", closed.closedAt());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("checklist", checklist);
        out.put("alarm", alarm);
        return out;
    }

    // ---------------------------------------------------------------
    // 웹 · 앱 공통
    // ---------------------------------------------------------------

    /** GET /checklists/{id} — 공정 담당자는 자기에게 온 것만 */
    public Map<String, Object> get(long checklistId) {
        UserScope scope = currentUser.scope();
        Checklist c = visible(checklistId, scope);
        Alarm a = alarmOrNull(c.alarmId());
        MasterCache.Snapshot m = masterCache.get();
        Event e = a == null ? null : a.mainEvent();
        Equipment eq = e == null ? null : m.equipment(e.equipmentId());

        List<Map<String, Object>> items = new ArrayList<>();
        for (Item i : c.items()) {
            items.add(itemRow(i));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", c.id());
        out.put("alarmId", c.alarmId());
        out.put("status", c.status());
        // 화면이 '경과 시간'을 시연 시계로 세도록 서버의 지금을 같이 줌
        out.put("serverTime", time.now());
        out.put("level", a == null ? null : Codes.level(a.stage()));
        out.put("processId", a == null ? null : a.processId());
        out.put("equipmentId", eq == null ? null : eq.id());
        out.put("equipmentName", eq == null ? null : eq.name());
        out.put("sensorTag", e == null ? null : e.tag());
        out.put("valueAtAlarm", e == null ? null : e.peak());
        out.put("unit", e == null ? null : e.unit());
        out.put("assignee", Map.of("id", c.assigneeId(), "name", c.assigneeName()));
        out.put("sentBy", c.sentByName());
        out.put("sentAt", c.sentAt());
        out.put("remindCount", c.remindCount());
        out.put("remindAvailableAt", c.lastRemindAt() == null ? null : c.lastRemindAt().plus(REMIND_COOLDOWN));
        out.put("items", items);
        out.put("reportedAt", c.reportedAt());
        out.put("confirmedAt", c.confirmedAt());
        return out;
    }

    // ---------------------------------------------------------------
    // 앱 (공정 담당자)
    // ---------------------------------------------------------------

    /** GET /me/checklists?status=ACTIVE|DONE */
    public List<Map<String, Object>> mine(String status) {
        UserScope scope = currentUser.require(UserScope.WORKER);
        List<String> statuses = "DONE".equals(status) ? List.of("REPORTED", "CONFIRMED", "CANCELLED")
                : List.of("SENT", "IN_PROGRESS");
        MasterCache.Snapshot m = masterCache.get();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Checklist c : repo.list(statuses, scope.userId(), null)) {
            Alarm a = alarmOrNull(c.alarmId());
            Event e = a == null ? null : a.mainEvent();
            Equipment eq = e == null ? null : m.equipment(e.equipmentId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.id());
            row.put("status", c.status());
            row.put("level", a == null ? null : Codes.level(a.stage()));
            row.put("title", (eq == null ? "" : eq.name() + " ")
                    + (e == null ? "" : Codes.sensorName(e.type()) + " " + DashboardFmt.num(e.peak()) + " " + e.unit()));
            row.put("sentAt", c.sentAt());
            row.put("doneCount", c.doneCount());
            row.put("totalCount", c.items().size());
            out.add(row);
        }
        return out;
    }

    /** PATCH /checklists/{id}/items/{itemId} */
    public Map<String, Object> checkItem(long checklistId, long itemId, boolean done, String result, String memo) {
        UserScope scope = currentUser.require(UserScope.WORKER);
        Checklist c = repo.find(checklistId);
        if (c == null) {
            throw ApiException.notFound("CHECKLIST_NOT_FOUND", "없는 체크리스트입니다.");
        }
        if (c.assigneeId() != scope.userId()) {
            throw ApiException.forbidden("NOT_ASSIGNEE", "담당자가 바뀌어 더 이상 내 체크리스트가 아닙니다.");
        }
        if (!"SENT".equals(c.status()) && !"IN_PROGRESS".equals(c.status())) {
            throw ApiException.conflict("CHECKLIST_CLOSED", "이미 완료 보고한 체크리스트입니다.");
        }
        OffsetDateTime now = time.now();
        if (repo.updateItem(checklistId, itemId, done, result, memo, now) == 0) {
            throw ApiException.notFound("ITEM_NOT_FOUND", "없는 항목입니다.");
        }
        if ("SENT".equals(c.status()) && done) {
            repo.setStatus(checklistId, "IN_PROGRESS");
        }
        Checklist after = repo.find(checklistId);
        Item item = after.items().stream().filter(i -> i.id() == itemId).findFirst().orElse(null);
        publish(after, item);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemId", itemId);
        out.put("doneAt", item == null ? null : item.doneAt());
        out.put("doneCount", after.doneCount());
        out.put("totalCount", after.items().size());
        return out;
    }

    /** POST /checklists/{id}/report */
    public Map<String, Object> report(long checklistId, String memo) {
        UserScope scope = currentUser.require(UserScope.WORKER);
        Checklist c = repo.find(checklistId);
        if (c == null) {
            throw ApiException.notFound("CHECKLIST_NOT_FOUND", "없는 체크리스트입니다.");
        }
        if (c.assigneeId() != scope.userId()) {
            throw ApiException.forbidden("NOT_ASSIGNEE", "담당자가 바뀌어 더 이상 내 체크리스트가 아닙니다.");
        }
        if (!"SENT".equals(c.status()) && !"IN_PROGRESS".equals(c.status())) {
            throw ApiException.conflict("CHECKLIST_CLOSED", "이미 완료 보고한 체크리스트입니다.");
        }
        if (c.doneCount() < c.items().size()) {
            throw ApiException.conflict("ITEMS_NOT_DONE", "체크하지 않은 항목이 있습니다.");
        }
        OffsetDateTime now = time.now();
        repo.report(checklistId, now);
        repo.addAction(c.alarmId(), "REPORT", c.assigneeName(), "OK", memo, scope.userId(), now);
        publish(repo.find(checklistId), null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", checklistId);
        out.put("status", "REPORTED");
        out.put("reportedAt", now);
        return out;
    }

    /** POST /me/push-token · DELETE /me/push-token */
    public Map<String, Object> pushToken(String token) {
        UserScope scope = currentUser.scope();
        repo.setFcmToken(scope.userId(), token == null || token.isBlank() ? null : token);
        return Map.of("pushReady", token != null && !token.isBlank());
    }

    // ---------------------------------------------------------------

    private Worker workerOf(long processId, long userId) {
        return repo.workers(processId).stream().filter(w -> w.id() == userId).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ASSIGNEE_NOT_IN_PROCESS",
                        "그 공정의 담당자가 아닙니다."));
    }

    private Checklist visible(long checklistId, UserScope scope) {
        Checklist c = repo.find(checklistId);
        if (c == null) {
            throw ApiException.notFound("CHECKLIST_NOT_FOUND", "없는 체크리스트입니다.");
        }
        if (scope.isWorker()) {
            if (c.assigneeId() != scope.userId()) {
                throw ApiException.outOfScope();
            }
            return c;
        }
        Alarm a = alarmOrNull(c.alarmId());
        if (a != null && !scope.canProcess(a.processId(), masterCache.get())) {
            throw ApiException.outOfScope();
        }
        return c;
    }

    private Alarm alarmOrNull(long alarmId) {
        try {
            return central.alarm(alarmId);
        } catch (ApiException e) {
            if (e.status() == HttpStatus.NOT_FOUND) {
                return null; // 시연 리셋으로 중앙 경보가 지워진 경우
            }
            throw e;
        }
    }

    private Map<String, Object> listRow(Checklist c, Alarm a, MasterCache.Snapshot m) {
        Event e = a.mainEvent();
        Equipment eq = e == null ? null : m.equipment(e.equipmentId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", c.id());
        row.put("alarmId", c.alarmId());
        row.put("status", c.status());
        row.put("level", Codes.level(a.stage()));
        // 목록 줄에도 설비 · 공정 번호 (GET /checklists/{id} 와 같은 값, docs/api/frontend-api-audit-1120.md 3장)
        row.put("processId", a.processId());
        row.put("equipmentId", eq == null ? null : eq.id());
        row.put("equipmentName", eq == null ? null : eq.name());
        row.put("sensorTag", e == null ? null : e.tag());
        row.put("assignee", Map.of("id", c.assigneeId(), "name", c.assigneeName()));
        row.put("doneCount", c.doneCount());
        row.put("totalCount", c.items().size());
        row.put("sentAt", c.sentAt());
        return row;
    }

    private static Map<String, Object> itemRow(Item i) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", i.id());
        row.put("seq", i.seq());
        row.put("content", i.content());
        row.put("doneAt", i.doneAt());
        row.put("result", i.result());
        row.put("memo", i.memo());
        return row;
    }

    /** SSE checklist.updated — 웹 담당 범위 사람들 + 담당 공정 담당자 */
    private void publish(Checklist c, Item item) {
        if (c == null) {
            return;
        }
        Alarm a = alarmOrNull(c.alarmId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", c.id());
        data.put("alarmId", c.alarmId());
        data.put("status", c.status());
        data.put("doneCount", c.doneCount());
        data.put("totalCount", c.items().size());
        data.put("assignee", Map.of("id", c.assigneeId(), "name", c.assigneeName()));
        data.put("item", item == null ? null : itemRow(item));
        long processId = a == null ? -1 : a.processId();
        hub.publish("checklist.updated", data, s -> s.scope().isWorker()
                ? s.scope().userId() == c.assigneeId()
                : s.scope().canProcess(processId, masterCache.get()));
    }

    /** 숫자 표시 (22.4, 18) */
    static final class DashboardFmt {
        private DashboardFmt() {
        }

        static String num(Double v) {
            if (v == null) {
                return "-";
            }
            return v == Math.rint(v) ? String.valueOf(v.longValue()) : String.valueOf(Math.round(v * 100) / 100.0);
        }
    }
}
