package com.gte.argos.gateway.checklist;

import com.gte.argos.gateway.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 체크리스트 API (API 명세서 C1 · C2 · M1 · M2) */
@RestController
@RequestMapping("/api/v1")
public class ChecklistController {

    private final ChecklistService service;

    public ChecklistController(ChecklistService service) {
        this.service = service;
    }

    /** @param sensorType CURRENT | TEMP | CO | IR */
    @GetMapping("/checklist-templates")
    public ApiResponse<Map<String, Object>> template(@RequestParam(required = false) String equipmentType,
                                                     @RequestParam String sensorType) {
        return ApiResponse.ok(service.template(equipmentType, sensorType));
    }

    @GetMapping("/processes/{processId}/workers")
    public ApiResponse<List<Map<String, Object>>> workers(@PathVariable long processId) {
        return ApiResponse.ok(service.workers(processId));
    }

    @PostMapping("/checklists")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> send(@RequestBody @Valid SendRequest body) {
        return ApiResponse.ok(service.send(body.alarmId(), body.templateId(), body.assigneeId(), body.items()));
    }

    /** @param status ACTIVE(진행 중 전체, 기본) | SENT | IN_PROGRESS | REPORTED | CONFIRMED */
    @GetMapping("/checklists")
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Long factoryId) {
        return ApiResponse.ok(service.list(status, factoryId));
    }

    @GetMapping("/checklists/{checklistId}")
    public ApiResponse<Map<String, Object>> get(@PathVariable long checklistId) {
        return ApiResponse.ok(service.get(checklistId));
    }

    @PostMapping("/checklists/{checklistId}/remind")
    public ApiResponse<Map<String, Object>> remind(@PathVariable long checklistId) {
        return ApiResponse.ok(service.remind(checklistId));
    }

    @PatchMapping("/checklists/{checklistId}/assignee")
    public ApiResponse<Map<String, Object>> reassign(@PathVariable long checklistId,
                                                     @RequestBody @Valid ReassignRequest body) {
        return ApiResponse.ok(service.reassign(checklistId, body.assigneeId(), body.reason()));
    }

    @PostMapping("/checklists/{checklistId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable long checklistId,
                                                    @RequestBody(required = false) ConfirmRequest body) {
        return ApiResponse.ok(service.confirm(checklistId, body == null ? null : body.result(),
                body == null ? null : body.memo()));
    }

    // 앱 --------------------------------------------------------------

    @GetMapping("/me/checklists")
    public ApiResponse<List<Map<String, Object>>> mine(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.mine(status));
    }

    @PatchMapping("/checklists/{checklistId}/items/{itemId}")
    public ApiResponse<Map<String, Object>> checkItem(@PathVariable long checklistId, @PathVariable long itemId,
                                                      @RequestBody @Valid ItemRequest body) {
        return ApiResponse.ok(service.checkItem(checklistId, itemId, body.done(), body.result(), body.memo()));
    }

    @PostMapping("/checklists/{checklistId}/report")
    public ApiResponse<Map<String, Object>> report(@PathVariable long checklistId,
                                                   @RequestBody(required = false) ReportRequest body) {
        return ApiResponse.ok(service.report(checklistId, body == null ? null : body.memo()));
    }

    @PostMapping("/me/push-token")
    public ApiResponse<Map<String, Object>> registerPushToken(@RequestBody PushTokenRequest body) {
        return ApiResponse.ok(service.pushToken(body.fcmToken()));
    }

    @DeleteMapping("/me/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePushToken() {
        service.pushToken(null);
    }

    // ---------------------------------------------------------------

    public record SendRequest(@NotNull Long alarmId, Long templateId, @NotNull Long assigneeId, List<String> items) {
    }

    public record ReassignRequest(@NotNull Long assigneeId, String reason) {
    }

    /** @param result RESOLVED | PARTS_PLANNED | NEED_RECHECK */
    public record ConfirmRequest(String result, String memo) {
    }

    /** @param result 이상 없음 | 조치함 | 부품 교체 예정 | 추가 점검 필요 */
    public record ItemRequest(@NotNull Boolean done, String result, String memo) {
    }

    public record ReportRequest(String memo) {
    }

    public record PushTokenRequest(String fcmToken, String platform, String appVersion) {
    }
}
