package com.gte.argos.gateway.push;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 공장 경고 방송 (방송 시스템, 외부 기존 시스템의 REST API).
 *
 * <p>argos.broadcast.url 이 있으면 아래처럼 부르고, 없으면 로그만 남긴다. 방송 시스템 API 가 정해지면 경로·본문만 맞추면 된다.
 * <pre>
 * POST {url}/start  { "requestId": "fire-12", "plantId": 1, "processId": 12, "message": "..." }
 * POST {url}/stop   { "requestId": "fire-12" }
 * </pre>
 */
@Component
public class BroadcastSender {

    private static final Logger log = LoggerFactory.getLogger(BroadcastSender.class);

    private final String url;
    private final RestClient rest;

    public BroadcastSender(@Value("${argos.broadcast.url:}") String url) {
        this.url = url == null ? "" : url.replaceAll("/+$", "");
        this.rest = this.url.isEmpty() ? null : RestClient.create();
    }

    /** @return 방송 시작 요청이 받아들여졌는지 */
    public boolean start(long fireId, long plantId, Long processId, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", "fire-" + fireId);
        body.put("plantId", plantId);
        body.put("processId", processId);
        body.put("message", message);
        return call("/start", body, "[방송 시작] " + message);
    }

    public boolean stop(long fireId) {
        return call("/stop", Map.of("requestId", "fire-" + fireId), "[방송 종료] fire=" + fireId);
    }

    private boolean call(String path, Map<String, Object> body, String logText) {
        if (rest == null) {
            log.info("{} (방송 시스템 미연결, 로그만)", logText);
            return true;
        }
        try {
            rest.post().uri(url + path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
            log.info("{} → 방송 시스템 요청 완료", logText);
            return true;
        } catch (RuntimeException e) {
            log.warn("{} → 방송 시스템 요청 실패: {}", logText, e.getMessage());
            return false;
        }
    }
}
