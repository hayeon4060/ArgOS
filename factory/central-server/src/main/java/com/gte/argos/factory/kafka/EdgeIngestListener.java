package com.gte.argos.factory.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.common.time.DemoClock;
import com.gte.argos.factory.ingest.LiveJudgementService;
import com.gte.argos.factory.judge.EdgeStage;
import com.gte.argos.factory.kafka.EdgeMessages.EdgeRaw;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 엣지(DAS) 센서값·단계 받기.
 * <pre>
 * argos.edge.raw   (1초마다 센서별)  → 1분 추이 · 실시간 현재값 (판정 안 함), COMM_LOST 는 lost_n
 * argos.edge.stage (단계 바뀔 때)    → 센서 이벤트 (START · UPDATE · END) → 관제 단위 조합 경보
 * </pre>
 * 시각은 받은 시각(서버 시계)으로 한다. 엣지 ts 는 쓰지 않는다 (봇이 CSV 시각을 5분마다 반복해서 보내므로).
 * argos.kafka.enabled=true 일 때만 생긴다.
 */
@Component
@ConditionalOnProperty(name = "argos.kafka.enabled", havingValue = "true")
public class EdgeIngestListener {

    private static final Logger log = LoggerFactory.getLogger(EdgeIngestListener.class);

    private final LiveJudgementService live;
    private final DemoClock clock;
    private final ObjectMapper json;
    private final AtomicLong unknownTags = new AtomicLong();

    public EdgeIngestListener(LiveJudgementService live, DemoClock clock, ObjectMapper json) {
        this.live = live;
        this.clock = clock;
        this.json = json;
    }

    @KafkaListener(topics = EdgeMessages.RAW_TOPIC)
    public void onRaw(String value) {
        try {
            EdgeRaw r = EdgeMessages.raw(json.readTree(value));
            if (r == null) {
                log.warn("엣지 센서값에 tag 가 없어 버림: {}", value);
                return;
            }
            if (!live.ingestEdgeValue(r.tag(), r.value(), r.lost()) && unknownTags.getAndIncrement() % 100 == 0) {
                log.warn("엣지 센서값: 모르는 태그 {} (지금까지 {}건)", r.tag(), unknownTags.get());
            }
        } catch (Exception e) {
            log.warn("엣지 센서값 처리 실패: {} ({})", value, e.getMessage());
        }
    }

    @KafkaListener(topics = EdgeMessages.STAGE_TOPIC)
    public void onStage(String value) {
        try {
            EdgeStage s = EdgeMessages.stage(json.readTree(value), clock.now());
            if (s == null) {
                log.warn("엣지 단계 메시지 형식이 맞지 않아 버림: {}", value);
                return;
            }
            log.info("엣지 단계: {} {} {} stage={} rule={} plc_ok={}", s.edgeKey(), s.phase(), s.tag(),
                    s.stage().code(), s.rule(), s.plcOk());
            live.ingestEdgeStage(s);
        } catch (Exception e) {
            log.warn("엣지 단계 처리 실패: {} ({})", value, e.getMessage());
        }
    }
}
