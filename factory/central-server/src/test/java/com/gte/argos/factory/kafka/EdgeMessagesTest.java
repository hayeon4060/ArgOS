package com.gte.argos.factory.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.kafka.EdgeMessages.EdgeResult;
import java.time.OffsetDateTime;
import java.util.List;
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
    void 센서값과_통신_끊김() throws Exception {
        var ok = EdgeMessages.raw(json.readTree(
                "{\"tag\":\"TE-305\",\"ts\":\"2026-11-16T09:02:15+09:00\",\"value\":152.4,\"quality\":\"GOOD\"}"));
        assertThat(ok.tag()).isEqualTo("TE-305");
        assertThat(ok.value()).isEqualTo(152.4);
        assertThat(ok.lost()).isFalse();
        var lost = EdgeMessages.raw(json.readTree("{\"tag\":\"TE-305\",\"value\":null,\"quality\":\"COMM_LOST\"}"));
        assertThat(lost.lost()).isTrue();
        assertThat(lost.value()).isNull();
        assertThat(EdgeMessages.raw(json.readTree("{\"value\":1}"))).isNull();
    }

    @Test
    void 단계_메시지_시각은_받은_시각() throws Exception {
        java.time.Instant now = java.time.Instant.parse("2026-11-16T01:30:00Z");
        var s = EdgeMessages.stage(json.readTree("""
                { "edge_key": "P5-TE-305-20261116T090145", "phase": "END", "tag": "TE-305", "threshold_id": 5,
                  "stage": 1, "prev_stage": 2, "max_stage": 3, "rule": "HI", "reason": "완화·복귀",
                  "ts": "2026-11-16T09:12:05+09:00", "value": 144.6, "peak_value": 170.2,
                  "plc_address": "D310,2", "plc_written": [1, 1446], "plc_ok": true }"""), now);

        assertThat(s.phase()).isEqualTo(com.gte.argos.factory.judge.EdgeStage.Phase.END);
        assertThat(s.stage()).isEqualTo(com.gte.argos.factory.judge.Stage.NORMAL);
        assertThat(s.thresholdId()).isEqualTo(5L);
        assertThat(s.peak()).isEqualTo(170.2);
        assertThat(s.plcOk()).isTrue();
        assertThat(s.ts()).isEqualTo(now);
        assertThat(EdgeMessages.stage(json.readTree(
                "{\"edge_key\":\"k\",\"phase\":\"START\",\"tag\":\"TE-305\",\"stage\":2,\"threshold_id\":null}"), now)
                .thresholdId()).isNull();
        assertThat(EdgeMessages.stage(json.readTree("{\"edge_key\":\"k\",\"phase\":\"BOOM\",\"tag\":\"T\",\"stage\":2}"),
                now)).isNull();
    }

    @Test
    void 기준값_메시지는_명세_필드와_엣지_연속_횟수() {
        var def = new com.gte.argos.factory.judge.SensorDef(313, "IT-303", "IT", 5,
                new com.gte.argos.factory.judge.ThresholdRule(8, 28.05, 31.88, 22.95, 17.85, 24, 6, 6, 0.05, null),
                List.of());
        Map<String, Object> m = EdgeMessages.threshold(def, 15, 15, 20);
        assertThat(m).containsEntry("threshold_id", 8L).containsEntry("tag", "IT-303")
                .containsEntry("warn_value", 28.05).containsEntry("low_alarm", 17.85)
                .containsEntry("immediate_value", null).containsEntry("warn_samples", 15)
                .containsEntry("alarm_samples", 15).containsEntry("release_samples", 20)
                .containsEntry("release_margin", 0.05);
        assertThat(EdgeMessages.threshold(new com.gte.argos.factory.judge.SensorDef(1, "X", "TE", 5, null, List.of()),
                15, 15, 20)).isNull();
    }

    @Test
    void command_id_가_없으면_null() throws Exception {
        assertThat(EdgeMessages.result(json.readTree("{\"result\":\"DONE\"}"))).isNull();
    }
}
