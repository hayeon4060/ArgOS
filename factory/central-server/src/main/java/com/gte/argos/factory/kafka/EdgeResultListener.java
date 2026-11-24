package com.gte.argos.factory.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.command.EquipmentCommandService;
import com.gte.argos.factory.kafka.EdgeMessages.EdgeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 엣지 명령 결과(argos.edge.cmd.result)를 받아 명령을 끝낸다.
 * 여러 서버(PC)가 같은 토픽을 받으므로, 이 서버가 보내고 아직 처리 중(PENDING)인 명령에만 반영한다
 * (EquipmentCommandService.applyEdgeResult). 컨슈머 그룹은 서버마다 다르게 (spring.kafka.consumer.group-id).
 */
@Component
@ConditionalOnProperty(name = "argos.kafka.enabled", havingValue = "true")
public class EdgeResultListener {

    private static final Logger log = LoggerFactory.getLogger(EdgeResultListener.class);

    private final EquipmentCommandService commands;
    private final ObjectMapper json;

    public EdgeResultListener(EquipmentCommandService commands, ObjectMapper json) {
        this.commands = commands;
        this.json = json;
    }

    @KafkaListener(topics = EdgeMessages.CMD_RESULT_TOPIC)
    public void onResult(String value) {
        try {
            EdgeResult r = EdgeMessages.result(json.readTree(value));
            if (r == null) {
                log.warn("엣지 명령 결과에 command_id 가 없어 버림: {}", value);
                return;
            }
            commands.applyEdgeResult(r);
        } catch (Exception e) {
            log.warn("엣지 명령 결과 처리 실패: {} ({})", value, e.getMessage());
        }
    }
}
