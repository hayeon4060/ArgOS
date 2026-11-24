package com.gte.argos.gateway.push;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 공장 경고 방송 (외부 기존 시스템 "방송 시스템"의 REST API, 아키텍처 그림의 중앙 서버 → 방송 시스템).
 * 화재로 판정되면 FireService 가 자동으로 start, 화재가 해제(수동 · 자동)되면 stop 을 부른다.
 *
 * <p>argos.broadcast.url 이 있으면 아래처럼 부르고, 없으면 로그만 남긴다.
 * <pre>
 * POST {url}/start   X-API-Key: {apiKey}(설정했을 때만)
 * { "requestId": "fire-3", "type": "FIRE", "plantId": 1, "factoryId": 5, "processId": 12, "equipmentId": 31,
 *   "location": "5공장 · 도장 공정 · 상도 건조로 버너실", "message": "...", "repeat": true, "detectedAt": "..." }
 * → 2xx { "broadcastId": "...", "status": "ON", "startedAt": "..." }
 *
 * POST {url}/stop    { "requestId": "fire-3", "reason": "CLEARED" | "AUTO_CLEARED" }
 * → 2xx { "status": "OFF", "endedAt": "..." }
 * </pre>
 * 같은 requestId 로 다시 오면 방송 시스템은 같은 방송으로 본다(재시도 때문에). 시작은 실패하면 한 번 더 시도한다.
 */
@Component
public class BroadcastSender {

    private static final Logger log = LoggerFactory.getLogger(BroadcastSender.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** 방송 시작 요청 */
    public record Start(long fireId, long plantId, Long factoryId, Long processId, Long equipmentId,
                        String location, String message, OffsetDateTime detectedAt) { }

    private final String url;
    private final String apiKey;
    private final RestClient rest;

    public BroadcastSender(@Value("${argos.broadcast.url:}") String url,
                           @Value("${argos.broadcast.api-key:}") String apiKey,
                           @Value("${argos.broadcast.timeout-ms:3000}") int timeoutMs) {
        this.url = url == null ? "" : url.strip().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        if (this.url.isEmpty()) {
            this.rest = null;
        } else {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(Duration.ofMillis(timeoutMs));
            f.setReadTimeout(Duration.ofMillis(timeoutMs));
            this.rest = RestClient.builder().requestFactory(f).build();
        }
    }

    /** @return 방송 시작 요청이 받아들여졌는지 (실패하면 한 번 더 시도) */
    public boolean start(Start s) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId(s.fireId()));
        body.put("type", "FIRE");
        body.put("plantId", s.plantId());
        body.put("factoryId", s.factoryId());
        body.put("processId", s.processId());
        body.put("equipmentId", s.equipmentId());
        body.put("location", s.location());
        body.put("message", s.message());
        body.put("repeat", true);
        body.put("detectedAt", s.detectedAt() == null ? null : s.detectedAt().format(TIME));
        String logText = "[방송 시작] fire=" + s.fireId() + " " + s.message();
        return call("/start", body, logText) || (rest != null && call("/start", body, logText + " (재시도)"));
    }

    /** @param reason CLEARED(비밀번호 해제) · AUTO_CLEARED(값이 정상으로 돌아와 자동 해제) */
    public boolean stop(long fireId, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", requestId(fireId));
        body.put("reason", reason);
        return call("/stop", body, "[방송 종료] fire=" + fireId + " " + reason);
    }

    static String requestId(long fireId) {
        return "fire-" + fireId;
    }

    private boolean call(String path, Map<String, Object> body, String logText) {
        if (rest == null) {
            log.info("{} (방송 시스템 미연결, 로그만)", logText);
            return true;
        }
        try {
            RestClient.RequestBodySpec req = rest.post().uri(url + path).contentType(MediaType.APPLICATION_JSON);
            if (!apiKey.isEmpty()) {
                req = req.header("X-API-Key", apiKey);
            }
            req.body(body).retrieve().toBodilessEntity();
            log.info("{} → 방송 시스템 요청 완료", logText);
            return true;
        } catch (RuntimeException e) {
            log.warn("{} → 방송 시스템 요청 실패: {}", logText, e.getMessage());
            return false;
        }
    }
}
