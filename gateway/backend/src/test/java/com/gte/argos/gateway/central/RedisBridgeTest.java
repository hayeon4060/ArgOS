package com.gte.argos.gateway.central;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gte.argos.gateway.central.CentralDto.CommandResult;
import com.gte.argos.gateway.central.CentralDto.FeedPage;
import com.gte.argos.gateway.common.api.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.http.HttpStatus;

class RedisBridgeTest {

    private final RedisBridge bridge = new RedisBridge(null, new ObjectMapper().registerModule(new JavaTimeModule()));

    private static MapRecord<String, Object, Object> rec(String boot, long seq, String type, String data) {
        return StreamRecords.newRecord().in(RedisKeys.FEED).withId(RecordId.of(1L, seq)).ofMap(
                Map.<Object, Object>of("bootId", boot, "seq", Long.toString(seq), "type", type,
                        "at", "2026-11-16T09:00:05+09:00", "data", data));
    }

    @Test
    void 스트림_항목을_피드_모양으로() {
        FeedPage p = bridge.page("b1", 10, 12, List.of(
                rec("b1", 11, "ALARM_OPENED", "{\"alarmId\":5}"),
                rec("b1", 12, "SENSOR_VALUES", "{\"values\":[[301,\"2026-11-16T09:00:05+09:00\",1.5]]}")));

        assertThat(p.bootId()).isEqualTo("b1");
        assertThat(p.gap()).isFalse();
        assertThat(p.next()).isEqualTo(12);
        assertThat(p.items()).extracting(CentralDto.FeedItem::type).containsExactly("ALARM_OPENED", "SENSOR_VALUES");
        assertThat(p.items().get(0).data()).containsEntry("alarmId", 5);
        assertThat(p.items().get(1).at().toString()).isEqualTo("2026-11-16T09:00:05+09:00");
    }

    @Test
    void 번호가_건너뛰면_gap() {
        FeedPage p = bridge.page("b1", 10, 20, List.of(rec("b1", 15, "RESET", "{}")));
        assertThat(p.gap()).isTrue();
        assertThat(p.next()).isEqualTo(15);
    }

    @Test
    void 새로_뜬_수집_서버_항목이면_새_bootId_로_알림() {
        FeedPage p = bridge.page("b1", 10, 10, List.of(rec("b2", 1, "RESET", "{}")));
        assertThat(p.bootId()).isEqualTo("b2");
        assertThat(p.items()).isEmpty();
    }

    @Test
    void 비었으면_after_그대로() {
        FeedPage p = bridge.page("b1", 10, 10, List.of());
        assertThat(p.next()).isEqualTo(10);
        assertThat(p.latest()).isEqualTo(10);
    }

    @Test
    void 응답_성공은_data_로() {
        CommandResult r = bridge.read("{\"success\":true,\"data\":{\"commandId\":7,\"equipmentId\":38,"
                + "\"command\":\"FAN_HZ\",\"status\":\"SENT\",\"value\":45.0,\"source\":\"USER\"}}", CommandResult.class);
        assertThat(r.commandId()).isEqualTo(7);
        assertThat(r.status()).isEqualTo("SENT");
    }

    @Test
    void 수집_서버_4xx_는_그대로_5xx_는_502() {
        assertThatThrownBy(() -> bridge.read("{\"success\":false,\"error\":{\"status\":409,"
                + "\"code\":\"COMMAND_NOT_AWAITING\",\"message\":\"승인 대기 명령이 아닙니다.\"}}", CommandResult.class))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.code()).isEqualTo("COMMAND_NOT_AWAITING");
                });
        assertThatThrownBy(() -> bridge.read("{\"success\":false,\"error\":{\"status\":500,\"code\":\"INTERNAL_ERROR\"}}",
                CommandResult.class))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> bridge.read("{\"success\":false,\"error\":{\"status\":504,\"code\":\"REQUEST_EXPIRED\"}}",
                CommandResult.class))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("REQUEST_EXPIRED"));
    }
}
