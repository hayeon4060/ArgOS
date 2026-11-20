package com.gte.argos.gateway.central;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.gateway.central.CentralDto.FeedItem;
import com.gte.argos.gateway.central.CentralDto.FeedPage;
import com.gte.argos.gateway.common.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 데이터 수집 서버와 Redis 로 주고받기 (ARGOS_REDIS_ENABLED=true 일 때만).
 *
 * <pre>
 * feed(after)  argos:feed 스트림을 기다려 읽는다 (HTTP /internal/feed 와 같은 FeedPage 모양)
 * call(op, …)  argos:cmd 스트림에 제어 명령을 넣고 argos:reply:{requestId} 응답을 기다린다 (최대 10초)
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "argos.redis.enabled", havingValue = "true")
public class RedisBridge {

    static final Duration REPLY_TIMEOUT = Duration.ofSeconds(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RedisBridge(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    // 실시간 변화 ---------------------------------------------------

    /**
     * after 번호 다음 변화를 최대 limit 건, 없으면 block 동안 기다린다.
     * after 가 0 이거나 수집 서버가 다시 켜졌으면(bootId 변경) 기다리지 않고 지금 번호만 알려 준다.
     */
    public FeedPage feed(String knownBootId, long after, int limit, Duration block) {
        Map<Object, Object> meta;
        try {
            meta = redis.opsForHash().entries(RedisKeys.FEED_META);
        } catch (DataAccessException e) {
            throw upstream();
        }
        if (meta.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "수집 서버 Redis 피드가 아직 없습니다.");
        }
        String bootId = String.valueOf(meta.get("bootId"));
        long bootMs = Long.parseLong(String.valueOf(meta.get("bootMs")));
        long latest = Long.parseLong(String.valueOf(meta.getOrDefault("latest", "0")));
        if (after <= 0 || !bootId.equals(knownBootId)) {
            return new FeedPage(bootId, List.of(), latest, latest, false);
        }
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redis.opsForStream().read(StreamReadOptions.empty().count(limit).block(block),
                    StreamOffset.create(RedisKeys.FEED, ReadOffset.from(RecordId.of(bootMs, after))));
        } catch (DataAccessException e) {
            throw upstream();
        }
        return page(bootId, after, latest, records == null ? List.of() : records);
    }

    /** 읽은 스트림 항목 → FeedPage. 첫 번호가 after+1 이 아니면 중간이 지워진 것(gap) */
    @SuppressWarnings("unchecked")
    FeedPage page(String bootId, long after, long latest, List<MapRecord<String, Object, Object>> records) {
        List<FeedItem> items = new ArrayList<>();
        boolean gap = false;
        for (MapRecord<String, Object, Object> r : records) {
            Map<Object, Object> f = r.getValue();
            if (!bootId.equals(String.valueOf(f.get("bootId")))) {
                // 수집 서버가 다시 켜진 뒤의 항목: 새 bootId 로 알려 처음부터 다시 따라가게 한다
                return new FeedPage(String.valueOf(f.get("bootId")), List.of(), 0, 0, false);
            }
            long seq = Long.parseLong(String.valueOf(f.get("seq")));
            if (items.isEmpty() && seq > after + 1) {
                gap = true;
            }
            String at = String.valueOf(f.getOrDefault("at", ""));
            Map<String, Object> data;
            try {
                data = mapper.readValue(String.valueOf(f.getOrDefault("data", "{}")), Map.class);
            } catch (JsonProcessingException e) {
                data = Map.of();
            }
            items.add(new FeedItem(seq, String.valueOf(f.get("type")), at.isEmpty() ? null : OffsetDateTime.parse(at),
                    data));
        }
        long next = items.isEmpty() ? after : items.get(items.size() - 1).seq();
        return new FeedPage(bootId, items, next, Math.max(latest, next), gap);
    }

    // 제어 명령 -----------------------------------------------------

    /** 제어 명령 1건을 보내고 응답 data 를 type 으로 돌려준다. 수집 서버 4xx 는 같은 상태·코드로 던진다 */
    public <T> T call(String op, Map<String, Object> payload, Class<T> type) {
        String requestId = UUID.randomUUID().toString();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("requestId", requestId);
        fields.put("op", op);
        fields.put("payload", write(payload));
        fields.put("sentAt", Long.toString(System.currentTimeMillis()));
        String reply;
        try {
            redis.execute((RedisCallback<RecordId>) c -> c.streamCommands().xAdd(
                    StreamRecords.newRecord().in(RedisKeys.CMD.getBytes(StandardCharsets.UTF_8)).ofMap(bytes(fields)),
                    XAddOptions.maxlen(RedisKeys.CMD_MAXLEN).approximateTrimming(true)));
            reply = redis.opsForList().rightPop(RedisKeys.REPLY_PREFIX + requestId, REPLY_TIMEOUT);
        } catch (DataAccessException e) {
            throw upstream();
        }
        if (reply == null) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "수집 서버가 명령에 응답하지 않습니다.");
        }
        return read(reply, type);
    }

    /** 응답 { success, data } | { success:false, error{status, code, message, details} } */
    @SuppressWarnings("unchecked")
    <T> T read(String reply, Class<T> type) {
        JsonNode n;
        try {
            n = mapper.readTree(reply);
        } catch (JsonProcessingException e) {
            throw upstream();
        }
        if (n.path("success").asBoolean(false)) {
            try {
                return mapper.treeToValue(n.get("data"), type);
            } catch (JsonProcessingException e) {
                throw upstream();
            }
        }
        JsonNode error = n.path("error");
        HttpStatus status = HttpStatus.resolve(error.path("status").asInt(502));
        if (status == null || status.is5xxServerError()) {
            if (status == HttpStatus.GATEWAY_TIMEOUT) {
                return failWith(status, error);
            }
            throw upstream();
        }
        return failWith(status, error);
    }

    @SuppressWarnings("unchecked")
    private <T> T failWith(HttpStatus status, JsonNode error) {
        Map<String, Object> details = error.hasNonNull("details") ? mapper.convertValue(error.get("details"), Map.class)
                : null;
        throw new ApiException(status, error.path("code").asText("UPSTREAM_ERROR"),
                error.path("message").asText("수집 서버가 명령을 거절했습니다."), details);
    }

    private String write(Object v) {
        try {
            return mapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<byte[], byte[]> bytes(Map<String, String> m) {
        Map<byte[], byte[]> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k.getBytes(StandardCharsets.UTF_8), v.getBytes(StandardCharsets.UTF_8)));
        return out;
    }

    private static ApiException upstream() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "수집 서버(Redis)에 연결할 수 없습니다.");
    }
}
