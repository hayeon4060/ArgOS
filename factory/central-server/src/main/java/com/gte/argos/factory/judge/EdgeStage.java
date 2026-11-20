package com.gte.argos.factory.judge;

import java.time.Instant;
import java.util.Objects;

/**
 * 엣지(DAS)가 보낸 센서 단계 변경 한 건 (Kafka argos.edge.stage).
 *
 * @param tag         센서 태그
 * @param edgeKey     구간 키 (P5-{태그}-{시작 시각}), 구간 내내 같음
 * @param phase       START · UPDATE · END
 * @param stage       바뀐 뒤 단계 (END 는 NORMAL)
 * @param rule        HI · LO · IMMEDIATE
 * @param thresholdId 엣지가 판정에 쓴 기준값 id. 서버 기준값을 아직 못 받았으면 null
 * @param value       그 순간 값 (없으면 null)
 * @param peak        END 의 구간 최고값(LO 면 최저값). 없으면 null
 * @param plcOk       PLC 단계 기록 성공 여부 (없으면 null)
 * @param ts          받은 시각 (서버 시계. 엣지 ts 는 쓰지 않음)
 */
public record EdgeStage(String tag, String edgeKey, Phase phase, Stage stage, String rule, Long thresholdId,
                        Double value, Double peak, Boolean plcOk, Instant ts) {

    public enum Phase { START, UPDATE, END }

    public EdgeStage {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(edgeKey, "edgeKey");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(ts, "ts");
    }
}
