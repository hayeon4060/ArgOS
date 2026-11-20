package com.gte.argos.factory.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.kafka.EdgeMessages.EdgeResult;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EdgeMessagesTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void 명령은_명세_필드_그대로() {
        Map<String, Object> m = EdgeMessages.command(101, 36, "ED-FAN", "FAN_HZ", 50.0, "USER",
                OffsetDateTime.parse("2026-11-20T10:04:30.789+09:00"));

        assertThat(m).containsExactly(Map.entry("command_id", 101L), Map.entry("equipment_id", 36L),
                Map.entry("equipment_code", "ED-FAN"), Map.entry("command", "FAN_HZ"), Map.entry("value", 50),
                Map.entry("source", "USER"), Map.entry("expires_at", "2026-11-20T10:04:30+09:00"));
    }

    @Test
    void 정지_명령은_value_없음() {
        assertThat(EdgeMessages.command(102, 36, "ED-FAN", "FAN_STOP", null, "USER",
                OffsetDateTime.parse("2026-11-20T10:04:30+09:00"))).doesNotContainKey("value");
    }

    @Test
    void 완료_결과() throws Exception {
        EdgeResult r = EdgeMessages.result(json.readTree("""
                {"command_id":101,"equipment_id":36,"command":"FAN_HZ","value":50,"source":"USER","result":"DONE",
                 "completed_at":"2026-11-16T09:03:41+09:00",
                 "detail":{"target_hz":50,"cmd_hz":50,"run_cmd":5058,"output_hz":49.8,"current_a":24.8,
                           "fan_rpm":1494,"message":"50Hz 도달"}}"""));

        assertThat(r.commandId()).isEqualTo(101);
        assertThat(r.equipmentId()).isEqualTo(36);
        assertThat(r.command()).isEqualTo("FAN_HZ");
        assertThat(r.result()).isEqualTo("DONE");
        assertThat(r.fanRpm()).isEqualTo(1494);
        assertThat(r.detail()).containsEntry("outputHz", 49.8).containsEntry("currentA", 24.8)
                .containsEntry("message", "50Hz 도달").containsEntry("edgeResult", "DONE")
                .containsEntry("edgeCompletedAt", "2026-11-16T09:03:41+09:00");
    }

    @Test
    void 회전수가_없으면_출력_Hz_로() throws Exception {
        EdgeResult r = EdgeMessages.result(json.readTree(
                "{\"command_id\":5,\"equipment_id\":36,\"command\":\"FAN_HZ\",\"result\":\"DONE\",\"detail\":{\"output_hz\":45}}"));
        assertThat(r.fanRpm()).isEqualTo(1350);
    }

    @Test
    void 거절_결과는_message_만() throws Exception {
        EdgeResult r = EdgeMessages.result(json.readTree("""
                {"command_id":102,"equipment_id":39,"command":"FAN_HZ","value":50,"source":"AUTO",
                 "result":"REJECTED","completed_at":"2026-11-16T09:03:40+09:00",
                 "detail":{"message":"equipment_id 39 인버터 없음"}}"""));

        assertThat(r.result()).isEqualTo("REJECTED");
        assertThat(r.fanRpm()).isNull();
        assertThat(r.detail()).containsEntry("message", "equipment_id 39 인버터 없음");
    }

    @Test
    void command_id_가_없으면_null() throws Exception {
        assertThat(EdgeMessages.result(json.readTree("{\"result\":\"DONE\"}"))).isNull();
    }
}
