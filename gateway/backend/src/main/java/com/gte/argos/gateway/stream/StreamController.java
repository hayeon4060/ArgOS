package com.gte.argos.gateway.stream;

import com.gte.argos.gateway.access.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실시간 연결 (API 명세서 '실시간 (SSE)').
 *
 * <pre>
 * GET /api/v1/stream?scope=process:12&thermal=331
 *   Authorization: Bearer … (브라우저 EventSource 는 헤더를 못 붙이므로 fetch-event-source 사용)
 *
 * 이벤트: sensor.value · level.changed · alarm.created · alarm.updated · checklist.updated ·
 *         command.result · reset · ping(15초)
 * sensor.value 는 scope 가 process 일 때만 온다. 나머지는 내 담당 범위 안의 것이 모두 온다.
 * </pre>
 * Last-Event-ID 로 놓친 이벤트 다시 받기는 아직 없다 (다시 연결하면 화면이 REST 로 다시 불러온다).
 */
@RestController
@RequestMapping("/api/v1")
public class StreamController {

    private final StreamHub hub;
    private final CurrentUser currentUser;

    public StreamController(StreamHub hub, CurrentUser currentUser) {
        this.hub = hub;
        this.currentUser = currentUser;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String scope,
                             @RequestParam(required = false) Long thermal) {
        return hub.subscribe(currentUser.scope(), scope, thermal);
    }
}
