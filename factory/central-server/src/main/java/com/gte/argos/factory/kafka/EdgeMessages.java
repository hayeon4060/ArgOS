package com.gte.argos.factory.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 엣지(DAS) Kafka 메시지 모양 (docs/ArgOS_Kafka_메시지_명세.md 3·4절). 필드 이름은 엣지 플로우와 같은 snake_case.
 */
public final class EdgeMessages {

    /** 서버 → 엣지 배기팬 명령 */
    public static final String CMD_TOPIC = "argos.edge.cmd";
    /** 엣지 → 서버 명령 결과 (key = 설비 ID 문자열) */
    public static final String CMD_RESULT_TOPIC = "argos.edge.cmd.result";

    /** 회전수 = Hz × 30 (엣지 detail.fan_rpm 이 없을 때) */
    private static final int RPM_PER_HZ = 30;

    private EdgeMessages() {
    }

    /**
     * argos.edge.cmd 메시지.
     *
     * @param expiresAt 실제 시각 기준 만료 시각 (엣지는 센서 데이터 ts 로 "지금"을 잡는다. 시연 시계가 아님)
     */
    public static Map<String, Object> command(long commandId, long equipmentId, String equipmentCode, String command,
                                              Double value, String source, OffsetDateTime expiresAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("command_id", commandId);
        m.put("equipment_id", equipmentId);
        m.put("equipment_code", equipmentCode);
        m.put("command", command);
        if (value != null) {
            m.put("value", value.intValue());
        }
        m.put("source", source);
        m.put("expires_at", expiresAt.truncatedTo(ChronoUnit.SECONDS).toString());
        return m;
    }

    /** argos.edge.cmd.result 메시지 → 결과. command_id 가 없으면 null */
    public static EdgeResult result(JsonNode n) {
        if (n == null || !n.hasNonNull("command_id")) {
            return null;
        }
        JsonNode d = n.path("detail");
        Map<String, Object> detail = new LinkedHashMap<>();
        put(detail, "targetHz", d.path("target_hz"));
        put(detail, "cmdHz", d.path("cmd_hz"));
        put(detail, "outputHz", d.path("output_hz"));
        put(detail, "currentA", d.path("current_a"));
        put(detail, "runCmd", d.path("run_cmd"));
        put(detail, "fanRpm", d.path("fan_rpm"));
        put(detail, "message", d.path("message"));
        put(detail, "edgeCompletedAt", n.path("completed_at"));
        String result = n.path("result").asText(null);
        detail.put("edgeResult", result);

        Integer rpm = null;
        if (d.path("fan_rpm").isNumber()) {
            rpm = (int) Math.round(d.path("fan_rpm").asDouble());
        } else if (d.path("output_hz").isNumber()) {
            rpm = (int) Math.round(d.path("output_hz").asDouble() * RPM_PER_HZ);
        }
        return new EdgeResult(n.path("command_id").asLong(), n.path("equipment_id").asLong(-1),
                n.path("command").asText(null), result, rpm, detail);
    }

    private static void put(Map<String, Object> out, String key, JsonNode v) {
        if (v.isNumber()) {
            out.put(key, v.numberValue());
        } else if (v.isTextual()) {
            out.put(key, v.asText());
        }
    }

    /**
     * @param result DONE | FAILED | REJECTED | EXPIRED | DUPLICATE (엣지 값 그대로)
     * @param fanRpm DONE 일 때 실제 회전수 (detail.fan_rpm, 없으면 output_hz × 30). 모르면 null
     * @param detail equipment_command.result_detail 에 합칠 값 (camelCase)
     */
    public record EdgeResult(long commandId, long equipmentId, String command, String result, Integer fanRpm,
                             Map<String, Object> detail) {
    }
}
