package com.gte.argos.factory.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.ingest.LiveJudgementService.DefinitionsChanged;
import com.gte.argos.factory.judge.SensorDef;
import com.gte.argos.factory.master.SensorDefinitionLoader;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 기준값을 엣지로 보낸다 (argos.config.threshold, key = 태그, 센서 1개 = 메시지 1개).
 * 서버가 켜질 때 모든 센서를 한 번씩, 기준값·센서를 바꿀 때마다 다시 모두 보낸다.
 * 열화상(IR)은 VMS 가 판정하므로 보내지 않는다. argos.kafka.enabled=true 일 때만 생긴다.
 */
@Component
@ConditionalOnProperty(name = "argos.kafka.enabled", havingValue = "true")
public class ThresholdPublisher {

    private static final Logger log = LoggerFactory.getLogger(ThresholdPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final SensorDefinitionLoader sensors;
    private final ObjectMapper json;
    private final int warnSamples;
    private final int alarmSamples;
    private final int releaseSamples;

    public ThresholdPublisher(KafkaTemplate<String, String> kafka, SensorDefinitionLoader sensors, ObjectMapper json,
                              @Value("${argos.kafka.edge-samples.warn:15}") int warnSamples,
                              @Value("${argos.kafka.edge-samples.alarm:15}") int alarmSamples,
                              @Value("${argos.kafka.edge-samples.release:20}") int releaseSamples) {
        this.kafka = kafka;
        this.sensors = sensors;
        this.json = json;
        this.warnSamples = warnSamples;
        this.alarmSamples = alarmSamples;
        this.releaseSamples = releaseSamples;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        publishAll("서버 시작");
    }

    /** 기준값·센서 저장이 커밋된 뒤 */
    @TransactionalEventListener(fallbackExecution = true)
    public void onChanged(DefinitionsChanged e) {
        publishAll("기준값 변경");
    }

    /** 보낸 메시지 수 */
    public int publishAll(String why) {
        int n = 0;
        try {
            for (SensorDef def : sensors.loadActive()) {
                if ("IR".equals(def.type())) {
                    continue;
                }
                Map<String, Object> m = EdgeMessages.threshold(def, warnSamples, alarmSamples, releaseSamples);
                if (m == null) {
                    continue;
                }
                kafka.send(EdgeMessages.THRESHOLD_TOPIC, def.tag(), json.writeValueAsString(m))
                        .whenComplete((ok, ex) -> {
                            if (ex != null) {
                                log.warn("기준값 보내기 실패 {}: {}", def.tag(), ex.getMessage());
                            }
                        });
                n++;
            }
            log.info("엣지로 기준값 {}개 보냄 ({}, 연속 횟수 {}·{}·{})", n, why, warnSamples, alarmSamples, releaseSamples);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("기준값 보내기 실패 ({}): {}", why, e.getMessage());
        }
        return n;
    }
}
