package com.gte.argos.factory.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.command.EquipmentCommandService;
import com.gte.argos.factory.command.EquipmentCommandService.CommandResult;
import com.gte.argos.factory.common.api.ApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RedisCommandListenerTest {

    private final EquipmentCommandService commands = mock(EquipmentCommandService.class);
    private final RedisCommandListener listener = new RedisCommandListener(null, new ObjectMapper(), commands);
    private final long now = 1_000_000;

    @Test
    void 설비_명령은_서비스에_넘기고_결과를_data_로() {
        CommandResult r = new CommandResult(7, 38, "FAN_HZ", "SENT", "RUN", 1350, 45.0, "USER");
        when(commands.request(38L, "FAN_HZ", 45.0, 3L, 2L)).thenReturn(r);

        Map<String, Object> out = listener.execute("COMMAND",
                "{\"equipmentId\":38,\"command\":\"FAN_HZ\",\"value\":45,\"alarmId\":3,\"userId\":2}",
                Long.toString(now), now);

        assertThat(out).containsEntry("success", true).containsEntry("data", r);
    }

    @Test
    void 승인_거절() {
        listener.execute("APPROVE", "{\"commandId\":9,\"userId\":2}", null, now);
        listener.execute("DENY", "{\"commandId\":10,\"userId\":2}", null, now);
        verify(commands).approve(9L, 2L);
        verify(commands).deny(10L, 2L);
    }

    @Test
    void 서비스_오류는_상태와_코드를_그대로() {
        when(commands.approve(anyLong(), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "COMMAND_NOT_AWAITING", "승인 대기 명령이 아닙니다."));

        Map<String, Object> out = listener.execute("APPROVE", "{\"commandId\":9,\"userId\":2}", null, now);

        assertThat(out).containsEntry("success", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) out.get("error");
        assertThat(error).containsEntry("status", 409).containsEntry("code", "COMMAND_NOT_AWAITING");
    }

    @Test
    void 오래된_요청은_실행하지_않음() {
        Map<String, Object> out = listener.execute("COMMAND", "{\"equipmentId\":38,\"command\":\"FAN_STOP\"}",
                Long.toString(now - RedisCommandListener.MAX_AGE_MS - 1), now);

        assertThat(out).containsEntry("success", false);
        assertThat(((Map<?, ?>) out.get("error")).get("code")).isEqualTo("REQUEST_EXPIRED");
        verify(commands, never()).request(anyLong(), any(), any(), any(), any());
    }

    @Test
    void 모르는_op_와_빠진_command() {
        assertThat(((Map<?, ?>) listener.execute("BOOM", "{}", null, now).get("error")).get("code"))
                .isEqualTo("UNKNOWN_OP");
        assertThat(((Map<?, ?>) listener.execute("COMMAND", "{\"equipmentId\":38}", null, now).get("error"))
                .get("code")).isEqualTo("VALIDATION_FAILED");
    }
}
