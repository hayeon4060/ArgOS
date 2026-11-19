package com.gte.argos.factory.command;

import com.gte.argos.factory.command.EquipmentCommandService.CommandResult;
import com.gte.argos.factory.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연계 서버 전용 설비 명령 API.
 *
 * <pre>
 * POST /api/v1/internal/equipment/{equipmentId}/commands
 *      { "command": "SHUTDOWN", "alarmId": 1051, "userId": 7 }
 *      { "command": "FAN_HZ", "value": 50, "userId": 7 }
 *      → 202 { commandId, status: "SENT" | "DUPLICATE" }. 완료는 피드 COMMAND_RESULT (DONE · FAILED · EXPIRED · CANCELLED)
 * GET  /api/v1/internal/commands/{commandId}
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/internal")
public class EquipmentCommandController {

    private final EquipmentCommandService service;

    public EquipmentCommandController(EquipmentCommandService service) {
        this.service = service;
    }

    @PostMapping("/equipment/{equipmentId}/commands")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CommandResult> request(@PathVariable long equipmentId, @RequestBody @Valid CommandRequest body) {
        return ApiResponse.ok(service.request(equipmentId, body.command(), body.value(), body.alarmId(), body.userId()));
    }

    /** 수동 배기팬 승인 대기 명령 승인 { userId } */
    @PostMapping("/commands/{commandId}/approve")
    public ApiResponse<CommandResult> approve(@PathVariable long commandId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.approve(commandId, user(body)));
    }

    /** 승인 대기 명령 거절 { userId } */
    @PostMapping("/commands/{commandId}/deny")
    public ApiResponse<CommandResult> deny(@PathVariable long commandId,
                                           @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(service.deny(commandId, user(body)));
    }

    private static Long user(Map<String, Object> body) {
        return body == null || body.get("userId") == null ? null : ((Number) body.get("userId")).longValue();
    }

    @GetMapping("/commands/{commandId}")
    public ApiResponse<CommandResult> find(@PathVariable long commandId) {
        return ApiResponse.ok(service.find(commandId));
    }

    /**
     * @param command FAN_HZ | FAN_STOP | SHUTDOWN | RESTART
     * @param value   FAN_HZ 일 때 목표 주파수 (20 ~ 60 Hz, 평상시 40)
     * @param alarmId 경보 대응 중 명령이면 그 경보
     * @param userId  누른 사람 (연계 DB app_user.user_id)
     */
    public record CommandRequest(@NotBlank String command, Double value, Long alarmId, Long userId) {
    }
}
