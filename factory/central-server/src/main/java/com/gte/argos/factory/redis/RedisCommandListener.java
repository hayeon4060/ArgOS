package com.gte.argos.factory.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.command.EquipmentCommandService;
import com.gte.argos.factory.common.api.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 중앙 서버 → 수집 서버 제어 명령 (Redis 스트림 argos:cmd, 소비 그룹 collector).
 *
 * <pre>
 * op = COMMAND  { equipmentId, command, value?, alarmId?, userId? }  설비 명령 (FAN_HZ · FAN_STOP · SHUTDOWN · RESTART)
 *      APPROVE  { commandId, userId }                             수동 배기팬 승인
 *      DENY     { commandId, userId }                             수동 배기팬 거절
 * 응답은 argos:reply:{requestId} 리스트에 1건 (60초 뒤 지워짐). HTTP 응답과 같은 { success, data | error } 모양
 * 보낸 지 15초가 지난 요청은 실행하지 않고 504 REQUEST_EXPIRED (중앙 서버가 이미 포기했으므로)
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "argos.redis.enabled", havingValue = "true")
public class RedisCommandListener {

    private static final Logger log = LoggerFactory.getLogger(RedisCommandListener.class);
    static final long MAX_AGE_MS = 15_000;

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final EquipmentCommandService commands;
    private final String consumer = "collector-" + ManagementFactory.getRuntimeMXBean().getName();

    private volatile boolean running;
    private Thread reader;
    private boolean failing;

    public RedisCommandListener(StringRedisTemplate redis, ObjectMapper json, EquipmentCommandService commands) {
        this.redis = redis;
        this.json = json;
        this.commands = commands;
    }

    @PostConstruct
    void start() {
        running = true;
        reader = new Thread(this::run, "redis-command-listener");
        reader.setDaemon(true);
        reader.start();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (reader != null) {
            reader.interrupt();
        }
    }

    private void run() {
        boolean grouped = false;
        while (running) {
            try {
                if (!grouped) {
                    ensureGroup();
                    grouped = true;
                    log.info("Redis 제어 명령 받기 켜짐: {} (그룹 {})", RedisKeys.CMD, RedisKeys.CMD_GROUP);
                }
                @SuppressWarnings("unchecked")
                List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                        Consumer.from(RedisKeys.CMD_GROUP, consumer),
                        StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),
                        StreamOffset.create(RedisKeys.CMD, ReadOffset.lastConsumed()));
                if (records != null) {
                    for (MapRecord<String, Object, Object> r : records) {
                        handle(r.getValue());
                        redis.opsForStream().acknowledge(RedisKeys.CMD, RedisKeys.CMD_GROUP, r.getId());
                    }
                }
                if (failing) {
                    log.info("Redis 제어 명령 받기 다시 연결됨");
                    failing = false;
                }
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                if (String.valueOf(e.getMessage()).contains("NOGROUP")) {
                    grouped = false; // Redis 가 비워졌으면 그룹부터 다시
                }
                if (!failing) {
                    log.warn("Redis 제어 명령을 읽지 못했습니다: {}", e.getMessage());
                    failing = true;
                }
                pause();
            }
        }
    }

    private void ensureGroup() {
        try {
            // 스트림이 아직 없으면 같이 만든다 (MKSTREAM)
            redis.execute((RedisCallback<String>) c -> c.streamCommands().xGroupCreate(
                    RedisKeys.CMD.getBytes(StandardCharsets.UTF_8), RedisKeys.CMD_GROUP, ReadOffset.latest(), true));
        } catch (RedisSystemException e) {
            if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    /** 요청 1건 실행 → 응답 리스트에 넣기 */
    void handle(Map<Object, Object> fields) {
        String requestId = str(fields.get("requestId"));
        if (requestId == null) {
            return;
        }
        Map<String, Object> reply = execute(str(fields.get("op")), str(fields.get("payload")),
                str(fields.get("sentAt")), System.currentTimeMillis());
        String key = RedisKeys.REPLY_PREFIX + requestId;
        redis.opsForList().leftPush(key, write(reply));
        redis.expire(key, Duration.ofSeconds(60));
    }

    Map<String, Object> execute(String op, String payload, String sentAt, long nowMs) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            if (sentAt != null && nowMs - Long.parseLong(sentAt) > MAX_AGE_MS) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "REQUEST_EXPIRED", "오래된 명령이라 실행하지 않았습니다.");
            }
            Map<String, Object> p = payload == null ? Map.of() : read(payload);
            Object data = switch (op == null ? "" : op) {
                case "COMMAND" -> {
                    String command = str(p.get("command"));
                    if (command == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "command 가 없습니다.");
                    }
                    yield commands.request(num(p.get("equipmentId")), command, dbl(p.get("value")),
                            lng(p.get("alarmId")), lng(p.get("userId")));
                }
                case "APPROVE" -> commands.approve(num(p.get("commandId")), lng(p.get("userId")));
                case "DENY" -> commands.deny(num(p.get("commandId")), lng(p.get("userId")));
                default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_OP", "모르는 명령입니다: " + op);
            };
            out.put("success", true);
            out.put("data", data);
        } catch (ApiException e) {
            out.put("success", false);
            out.put("error", error(e.status().value(), e.code(), e.getMessage(), e.details()));
        } catch (RuntimeException e) {
            log.warn("Redis 제어 명령 {} 실패", op, e);
            out.put("success", false);
            out.put("error", error(500, "INTERNAL_ERROR", "수집 서버에서 명령을 처리하지 못했습니다.", null));
        }
        return out;
    }

    private static Map<String, Object> error(int status, String code, String message, Map<String, Object> details) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("status", status);
        e.put("code", code);
        e.put("message", message);
        e.put("details", details);
        return e;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String payload) {
        try {
            return json.readValue(payload, Map.class);
        } catch (JsonProcessingException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "payload 가 JSON 이 아닙니다.");
        }
    }

    private String write(Object v) {
        try {
            return json.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            return "{\"success\":false}";
        }
    }

    private static long num(Object v) {
        if (v == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "번호가 없습니다.");
        }
        return ((Number) v).longValue();
    }

    private static Long lng(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private static Double dbl(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }

    private static String str(Object v) {
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }

    private static void pause() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
