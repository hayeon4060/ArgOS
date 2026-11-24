package com.gte.argos.factory.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 배기팬 명령을 엣지로 보낸다 (argos.edge.cmd, key = 설비 ID 문자열). argos.kafka.enabled=true 일 때만 생긴다.
 * 이 빈이 있으면 EquipmentCommandService 는 FAN_HZ · FAN_STOP 을 시뮬레이션하지 않고 엣지 결과를 기다린다.
 */
@Component
@ConditionalOnProperty(name = "argos.kafka.enabled", havingValue = "true")
public class EdgeCommandPublisher {

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;

    public EdgeCommandPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    /** 보내기. 브로커에 기록되면 완료되는 future (실패하면 예외로 완료) */
    public CompletableFuture<?> send(long equipmentId, Map<String, Object> message) {
        String body;
        try {
            body = json.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
        return kafka.send(EdgeMessages.CMD_TOPIC, Long.toString(equipmentId), body);
    }
}
