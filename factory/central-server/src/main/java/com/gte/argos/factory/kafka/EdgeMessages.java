package com.gte.argos.factory.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.gte.argos.factory.judge.EdgeStage;
import com.gte.argos.factory.judge.SensorDef;
import com.gte.argos.factory.judge.Stage;
import com.gte.argos.factory.judge.ThresholdRule;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 엣지(DAS) Kafka 메시지 모양 (docs/ArgOS_Kafka_메시지_명세.md). 필드 이름은 엣지 플로우와 같은 snake_case.
 * 엣지가 보낸 ts 는 쓰지 않는다: 값·단계의 시각은 받은 시각(서버 시계)으로 한다 (2026-11-20 결정).
 */
public final class EdgeMessages {

    /** 엣지 → 서버 1초 센서값 (key = 태그) */
    public static final String RAW_TOPIC = "argos.edge.raw";
    /** 엣지 → 서버 센서 단계 변경 */
    public static final String STAGE_TOPIC = "argos.edge.stage";
    /** 서버 → 엣지 기준값 (key = 태그, 압축 토픽 권장: 엣지가 처음부터 읽음) */
    public static final String THRESHOLD_TOPIC = "argos.config.threshold";
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

    /** argos.edge.raw → 값. tag 가 없으면 null. quality COMM_LOST 이거나 value 가 없으면 lost */
    public static EdgeRaw raw(JsonNode n) {
        String tag = n == null ? null : n.path("tag").asText(null);
        if (tag == null || tag.isBlank()) {
            return null;
        }
        JsonNode v = n.path("value");
        Double value = v.isNumber() ? v.asDouble() : null;
        boolean lost = "COMM_LOST".equals(n.path("quality").asText(null)) || value == null;
        return new EdgeRaw(tag, lost ? null : value, lost);
    }

    /**
     * argos.edge.stage → 단계 변경. 필수 필드(edge_key · phase · tag · stage)가 없거나 이상하면 null.
     *
     * @param receivedAt 받은 시각 (서버 시계)
     */
    public static EdgeStage stage(JsonNode n, Instant receivedAt) {
        if (n == null) {
            return null;
        }
        String edgeKey = n.path("edge_key").asText(null);
        String tag = n.path("tag").asText(null);
        EdgeStage.Phase phase;
        Stage stage;
        try {
            phase = EdgeStage.Phase.valueOf(n.path("phase").asText(""));
            stage = switch (n.path("stage").asInt(-1)) {
                case 1 -> Stage.NORMAL;
                case 2 -> Stage.WARN;
                case 3 -> Stage.ALARM;
                default -> throw new IllegalArgumentException("stage");
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (edgeKey == null || edgeKey.isBlank() || tag == null || tag.isBlank()) {
            return null;
        }
        JsonNode th = n.path("threshold_id");
        JsonNode plc = n.path("plc_ok");
        return new EdgeStage(tag, edgeKey, phase, stage, n.path("rule").asText(null),
                th.isNumber() ? th.asLong() : null, num(n.path("value")), num(n.path("peak_value")),
                plc.isBoolean() ? plc.asBoolean() : null, receivedAt);
    }

    /**
     * argos.config.threshold 메시지 (센서 1개). 값은 DB 기준값, 연속 횟수는 엣지 1초 샘플 기준(팀 결정 15 · 15 · 20).
     * 기준값이 없는 센서는 null.
     */
    public static Map<String, Object> threshold(SensorDef def, int warnSamples, int alarmSamples, int releaseSamples) {
        ThresholdRule r = def.rule();
        if (r == null || (r.warn() == null && r.alarm() == null)) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("threshold_id", r.thresholdId());
        m.put("tag", def.tag());
        m.put("warn_value", r.warn());
        m.put("alarm_value", r.alarm());
        m.put("low_warn", r.lowWarn());
        m.put("low_alarm", r.lowAlarm());
        m.put("immediate_value", r.immediate());
        m.put("warn_samples", warnSamples);
        m.put("alarm_samples", alarmSamples);
        m.put("release_samples", releaseSamples);
        m.put("release_margin", r.releaseMargin());
        return m;
    }

    private static Double num(JsonNode v) {
        return v.isNumber() ? v.asDouble() : null;
    }

    private static void put(Map<String, Object> out, String key, JsonNode v) {
        if (v.isNumber()) {
            out.put(key, v.numberValue());
        } else if (v.isTextual()) {
            out.put(key, v.asText());
        }
    }

    /** 1초 값 한 건. lost 면 value 는 null */
    public record EdgeRaw(String tag, Double value, boolean lost) {
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
