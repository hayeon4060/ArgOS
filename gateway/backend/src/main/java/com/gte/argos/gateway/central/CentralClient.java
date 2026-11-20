package com.gte.argos.gateway.central;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.gateway.central.CentralDto.Alarm;
import com.gte.argos.gateway.central.CentralDto.Clock;
import com.gte.argos.gateway.central.CentralDto.CommandResult;
import com.gte.argos.gateway.central.CentralDto.Envelope;
import com.gte.argos.gateway.central.CentralDto.Event;
import com.gte.argos.gateway.central.CentralDto.FeedPage;
import com.gte.argos.gateway.central.CentralDto.Master;
import com.gte.argos.gateway.central.CentralDto.Page;
import com.gte.argos.gateway.central.CentralDto.Sample;
import com.gte.argos.gateway.central.CentralDto.Series;
import com.gte.argos.gateway.central.CentralDto.Status;
import com.gte.argos.gateway.common.api.ApiException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

/**
 * 중앙 서버 연계 API 호출. 중앙 → 연계는 HTTP 로 정해졌다 (Redis 는 미니 PC → 중앙 구간만).
 * (2026-11-20 아키텍처) ARGOS_REDIS_ENABLED=true 면 제어 명령(설비 명령 · 승인 · 거절)은 Redis(argos:cmd)로 보낸다.
 * 조회는 아직 HTTP.
 *
 * <pre>
 * - 응답의 { success, data } 를 벗겨 data 만 돌려준다
 * - 중앙 서버의 4xx 오류는 같은 상태·코드·메시지로 그대로 던진다 (예: 409 VALUE_STILL_OVER)
 * - 연결 실패·5xx 는 502 UPSTREAM_ERROR
 * </pre>
 */
@Component
public class CentralClient {

    private final RestClient rest;
    private final ObjectMapper mapper;
    private RedisBridge redis;

    public CentralClient(
            @Value("${argos.central.base-url:http://localhost:8081}") String baseUrl,
            @Value("${argos.central.api-key:}") String apiKey,
            ObjectMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(10_000);
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(factory);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-Argos-Internal-Key", apiKey);
        }
        this.rest = builder.build();
        this.mapper = mapper;
    }

    /** Redis 를 켰을 때만 주입됨 (없으면 전부 HTTP) */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRedis(RedisBridge redis) {
        this.redis = redis;
    }

    public Master master() {
        return get(u -> u.path("/api/v1/internal/master").build(), new ParameterizedTypeReference<Envelope<Master>>() { });
    }

    public Status status(Long processId) {
        return get(u -> {
            u.path("/api/v1/internal/status");
            if (processId != null) {
                u.queryParam("processId", processId);
            }
            return u.build();
        }, new ParameterizedTypeReference<Envelope<Status>>() { });
    }

    public Series series(Collection<Long> sensorIds, OffsetDateTime from, OffsetDateTime to, String step) {
        String ids = sensorIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return get(u -> {
            u.path("/api/v1/internal/series").queryParam("sensorIds", ids);
            if (from != null) {
                u.queryParam("from", param(from));
            }
            if (to != null) {
                u.queryParam("to", param(to));
            }
            if (step != null && !step.isBlank()) {
                u.queryParam("step", step);
            }
            return u.build();
        }, new ParameterizedTypeReference<Envelope<Series>>() { });
    }

    public List<Event> events(EventQuery q) {
        return get(u -> {
            u.path("/api/v1/internal/events");
            q.params().forEach((k, v) -> {
                if (v != null) {
                    u.queryParam(k, param(v));
                }
            });
            return u.build();
        }, new ParameterizedTypeReference<Envelope<List<Event>>>() { });
    }

    public List<Sample> samples(long eventId) {
        return get(u -> u.path("/api/v1/internal/events/{id}/samples").build(eventId),
                new ParameterizedTypeReference<Envelope<List<Sample>>>() { });
    }

    public Page<Alarm> alarms(AlarmQuery q) {
        return get(u -> {
            u.path("/api/v1/internal/alarms");
            q.params().forEach((k, v) -> {
                if (v != null) {
                    u.queryParam(k, param(v));
                }
            });
            return u.build();
        }, new ParameterizedTypeReference<Envelope<Page<Alarm>>>() { });
    }

    public Alarm alarm(long alarmId) {
        return get(u -> u.path("/api/v1/internal/alarms/{id}").build(alarmId),
                new ParameterizedTypeReference<Envelope<Alarm>>() { });
    }

    /** 중앙 서버가 저장한 파일 (VMS 열화상 사진). [내용, 형식] */
    public org.springframework.http.ResponseEntity<byte[]> file(long fileId) {
        try {
            return rest.get().uri("/api/v1/internal/files/{id}", fileId).retrieve().toEntity(byte[].class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw upstream();
        }
    }

    public CommandResult findCommand(long commandId) {
        return get(u -> u.path("/api/v1/internal/commands/{id}").build(commandId),
                new ParameterizedTypeReference<Envelope<CommandResult>>() { });
    }

    /** 수동 배기팬 승인 대기 명령 승인 · 거절 (decision = approve | deny) */
    public CommandResult decideCommand(long commandId, String decision, Long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        if (redis != null) {
            body.put("commandId", commandId);
            return redis.call("approve".equals(decision) ? "APPROVE" : "DENY", body, CommandResult.class);
        }
        return post("/api/v1/internal/commands/" + commandId + "/" + decision, body,
                new ParameterizedTypeReference<Envelope<CommandResult>>() { });
    }

    public CentralDto.AlarmSeries alarmSeries(long alarmId) {
        return get(u -> u.path("/api/v1/internal/alarms/{id}/series").build(alarmId),
                new ParameterizedTypeReference<Envelope<CentralDto.AlarmSeries>>() { });
    }

    /** 설비 설정 바로 저장 { active?, fanAuto? } */
    public Map<String, Object> equipmentSettings(long equipmentId, Map<String, Object> body) {
        // 기본 HTTP 클라이언트(HttpURLConnection)는 PATCH 를 못 보내므로 POST 로 보낸다 (중앙은 둘 다 받음)
        return send("POST", "/api/v1/internal/equipment/" + equipmentId + "/settings", body,
                new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public Alarm ackAlarm(long alarmId, Long userId) {
        return post("/api/v1/internal/alarms/" + alarmId + "/ack", actor(userId),
                new ParameterizedTypeReference<Envelope<Alarm>>() { });
    }

    public Alarm actingAlarm(long alarmId, Long userId) {
        return post("/api/v1/internal/alarms/" + alarmId + "/acting", actor(userId),
                new ParameterizedTypeReference<Envelope<Alarm>>() { });
    }

    public Alarm closeAlarm(long alarmId, Long userId) {
        return post("/api/v1/internal/alarms/" + alarmId + "/close", actor(userId),
                new ParameterizedTypeReference<Envelope<Alarm>>() { });
    }

    public CommandResult command(long equipmentId, String command, Double value, Long alarmId, Long userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("command", command);
        body.put("value", value);
        body.put("alarmId", alarmId);
        body.put("userId", userId);
        if (redis != null) {
            body.put("equipmentId", equipmentId);
            return redis.call("COMMAND", body, CommandResult.class);
        }
        return post("/api/v1/internal/equipment/" + equipmentId + "/commands", body,
                new ParameterizedTypeReference<Envelope<CommandResult>>() { });
    }

    // 기준값 ------------------------------------------------------------

    public Map<String, Object> equipmentThresholds(long equipmentId) {
        return get(u -> u.path("/api/v1/internal/equipment/{id}/thresholds").build(equipmentId),
                new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public Map<String, Object> saveThresholds(long equipmentId, Map<String, Object> body) {
        return send("PUT", "/api/v1/internal/equipment/" + equipmentId + "/thresholds", body,
                new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public Map<String, Object> simulateThreshold(Map<String, Object> body) {
        return post("/api/v1/internal/thresholds/simulate", body,
                new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public List<Map<String, Object>> thresholdHistory(Long equipmentId, Long sensorId, String season,
                                                      OffsetDateTime from, OffsetDateTime to) {
        return get(u -> {
            u.path("/api/v1/internal/thresholds/history");
            if (equipmentId != null) {
                u.queryParam("equipmentId", equipmentId);
            }
            if (sensorId != null) {
                u.queryParam("sensorId", sensorId);
            }
            if (season != null && !season.isBlank()) {
                u.queryParam("season", season);
            }
            if (from != null) {
                u.queryParam("from", param(from));
            }
            if (to != null) {
                u.queryParam("to", param(to));
            }
            return u.build();
        }, new ParameterizedTypeReference<Envelope<List<Map<String, Object>>>>() { });
    }

    public Map<String, Object> updateSensor(long sensorId, Map<String, Object> body) {
        return send("PUT", "/api/v1/internal/sensors/" + sensorId, body,
                new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    // 공장 관리 (H1) --------------------------------------------------

    public Map<String, Object> createPlant(Map<String, Object> body) {
        return post("/api/v1/internal/plants", body, new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public Map<String, Object> createFactory(Map<String, Object> body) {
        return post("/api/v1/internal/factories", body, new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public Map<String, Object> createProcess(Map<String, Object> body) {
        return post("/api/v1/internal/processes", body, new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public Map<String, Object> layoutMeta(long processId) {
        return get(u -> u.path("/api/v1/internal/processes/{id}/layout-meta").build(processId),
                new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public Map<String, Object> saveLayout(long processId, Map<String, Object> body) {
        return send("PUT", "/api/v1/internal/processes/" + processId + "/layout", body,
                new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public Map<String, Object> setFloorplan(long processId, Map<String, Object> body) {
        return send("PUT", "/api/v1/internal/processes/" + processId + "/floorplan", body,
                new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
    }

    public FeedPage feed(long after, int limit) {
        return get(u -> u.path("/api/v1/internal/feed").queryParam("after", after).queryParam("limit", limit).build(),
                new ParameterizedTypeReference<Envelope<FeedPage>>() { });
    }

    public Clock clock() {
        return get(u -> u.path("/api/v1/system/clock").build(), new ParameterizedTypeReference<Envelope<Clock>>() { });
    }

    // ---------------------------------------------------------------

    private <T> T get(java.util.function.Function<UriBuilder, java.net.URI> uri,
                      ParameterizedTypeReference<Envelope<T>> type) {
        try {
            Envelope<T> env = rest.get().uri(uri).retrieve().body(type);
            return unwrap(env);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw upstream();
        }
    }

    private <T> T post(String path, Object body, ParameterizedTypeReference<Envelope<T>> type) {
        try {
            Envelope<T> env = rest.post().uri(path).body(body).retrieve().body(type);
            return unwrap(env);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw upstream();
        }
    }

    private <T> T send(String method, String path, Object body, ParameterizedTypeReference<Envelope<T>> type) {
        try {
            Envelope<T> env = rest.method(org.springframework.http.HttpMethod.valueOf(method)).uri(path).body(body)
                    .retrieve().body(type);
            return unwrap(env);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw upstream();
        }
    }

    private static <T> T unwrap(Envelope<T> env) {
        if (env == null || !env.success()) {
            throw upstream();
        }
        return env.data();
    }

    /** 중앙 서버 4xx 는 코드·메시지·details 를 그대로 넘긴다 */
    @SuppressWarnings("unchecked")
    private ApiException translate(RestClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            return upstream();
        }
        try {
            JsonNode error = mapper.readTree(e.getResponseBodyAsString()).path("error");
            String code = error.path("code").asText("UPSTREAM_ERROR");
            String message = error.path("message").asText("중앙 서버 요청이 거절되었습니다.");
            Map<String, Object> details = error.hasNonNull("details")
                    ? mapper.convertValue(error.get("details"), Map.class) : null;
            return new ApiException(status, code, message, details);
        } catch (Exception parse) {
            return new ApiException(status, "UPSTREAM_ERROR", "중앙 서버 요청이 거절되었습니다.");
        }
    }

    private static ApiException upstream() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "중앙 서버에 연결할 수 없습니다.");
    }

    // ---------------------------------------------------------------

    /** 이벤트 조회 조건 (null 은 빼고 보냄) */
    public record EventQuery(OffsetDateTime from, OffsetDateTime to, Long sensorId, Long equipmentId, Long processId,
                             Boolean openOnly, Integer limit) {
        Map<String, Object> params() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", from);
            m.put("to", to);
            m.put("sensorId", sensorId);
            m.put("equipmentId", equipmentId);
            m.put("processId", processId);
            m.put("openOnly", openOnly);
            m.put("limit", limit);
            return m;
        }
    }

    /** 경보 조회 조건 (null 은 빼고 보냄). status 는 쉼표로 여러 개 */
    public record AlarmQuery(OffsetDateTime from, OffsetDateTime to, String status, Integer minStage, Long unitId,
                             Long processId, Long factoryId, Long plantId, Integer page, Integer size) {
        Map<String, Object> params() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", from);
            m.put("to", to);
            m.put("status", status);
            m.put("minStage", minStage);
            m.put("unitId", unitId);
            m.put("processId", processId);
            m.put("factoryId", factoryId);
            m.put("plantId", plantId);
            m.put("page", page);
            m.put("size", size);
            return m;
        }
    }

    /** 시각은 UTC(Z) 로 보낸다. "+09:00" 의 + 가 쿼리에서 공백으로 바뀌는 문제를 피하려고 */
    private static String param(Object v) {
        if (v instanceof OffsetDateTime o) {
            return o.toInstant().toString();
        }
        if (v instanceof Instant i) {
            return i.toString();
        }
        return v.toString();
    }

    private static Map<String, Object> actor(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        return m;
    }
}
