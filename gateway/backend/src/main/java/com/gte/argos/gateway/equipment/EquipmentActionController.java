package com.gte.argos.gateway.equipment;

import com.gte.argos.gateway.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 설비 조작 API. 명령 결과는 SSE command.result (팬 5초, 셧다운 10초 안에 안 오면 화면이 실패 처리) */
@RestController
@RequestMapping("/api/v1")
public class EquipmentActionController {

    private final EquipmentActionService service;

    public EquipmentActionController(EquipmentActionService service) {
        this.service = service;
    }

    @PostMapping("/equipment/{equipmentId}/fan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, Object>> fan(@PathVariable long equipmentId, @RequestBody FanRequest body) {
        return ApiResponse.ok(service.fan(equipmentId, body.power(), body.rpm(), body.hz()));
    }

    /** 수동 배기팬: 가스 알림 때 승인 대기(fanAuto.status = AWAITING)인 명령 승인 */
    @PostMapping("/fan-commands/{commandId}/approve")
    public ApiResponse<Map<String, Object>> approveFan(@PathVariable long commandId) {
        return ApiResponse.ok(service.decideFan(commandId, true));
    }

    /** 승인 대기 명령 거절 (fanAuto.status = DENIED) */
    @PostMapping("/fan-commands/{commandId}/deny")
    public ApiResponse<Map<String, Object>> denyFan(@PathVariable long commandId) {
        return ApiResponse.ok(service.decideFan(commandId, false));
    }

    @PostMapping("/equipment/{equipmentId}/shutdown")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, Object>> shutdown(@PathVariable long equipmentId,
                                                     @RequestBody(required = false) ShutdownRequest body) {
        return ApiResponse.ok(service.shutdown(equipmentId, body == null ? null : body.alarmId(),
                body == null ? null : body.reason()));
    }

    @PostMapping("/processes/{processId}/shutdown")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Map<String, Object>> shutdownProcess(@PathVariable long processId,
                                                            @RequestBody(required = false) ProcessShutdownRequest body) {
        return ApiResponse.ok(service.shutdownProcess(processId, body == null ? null : body.alarmId(),
                body == null ? null : body.equipmentIds(), body == null ? null : body.reason()));
    }

    @PostMapping("/processes/{processId}/call-workers")
    public ApiResponse<Map<String, Object>> callWorkers(@PathVariable long processId,
                                                        @RequestBody(required = false) CallRequest body) {
        return ApiResponse.ok(service.callWorkers(processId, body == null ? null : body.alarmId()));
    }

    /** @param power ON | OFF, @param rpm 600 ~ 1800 (둘 중 하나) */
    /** power(ON·OFF), rpm(600~1800), hz(20~60) 중 하나 */
    public record FanRequest(String power, Integer rpm, Integer hz) {
    }

    public record ShutdownRequest(Long alarmId, String reason) {
    }

    public record ProcessShutdownRequest(Long alarmId, List<Long> equipmentIds, String reason) {
    }

    public record CallRequest(Long alarmId) {
    }
}
