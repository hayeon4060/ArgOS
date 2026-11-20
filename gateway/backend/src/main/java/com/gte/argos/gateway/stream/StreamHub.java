package com.gte.argos.gateway.stream;

import com.gte.argos.gateway.access.UserScope;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 열려 있는 SSE 연결 모음. 이벤트를 받을 사람(권한·담당 범위·지금 보는 범위)을 골라 보낸다.
 */
@Component
public class StreamHub {

    private static final Logger log = LoggerFactory.getLogger(StreamHub.class);
    private static final long TIMEOUT_MS = 60L * 60 * 1000; // 1시간. 끊기면 프론트가 다시 연결

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(UserScope scope, String viewScope, Long thermal) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Subscriber sub = Subscriber.of(emitter, scope, viewScope, thermal);
        subscribers.add(sub);
        emitter.onCompletion(() -> subscribers.remove(sub));
        emitter.onTimeout(() -> subscribers.remove(sub));
        emitter.onError(e -> subscribers.remove(sub));
        try {
            emitter.send(SseEmitter.event().name("ping").data("{}", MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            subscribers.remove(sub);
        }
        return emitter;
    }

    /** 고른 사람에게 이벤트 하나 */
    public void publish(String name, Object data, Predicate<Subscriber> to) {
        publish(name, null, data, to);
    }

    public void publish(String name, String id, Object data, Predicate<Subscriber> to) {
        for (Subscriber s : subscribers) {
            if (!to.test(s)) {
                continue;
            }
            try {
                SseEmitter.SseEventBuilder ev = SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON);
                if (id != null) {
                    ev.id(id);
                }
                s.emitter().send(ev);
            } catch (IOException | IllegalStateException e) {
                subscribers.remove(s);
                log.debug("SSE 연결 정리: {}", e.getMessage());
            }
        }
    }

    /** 이 사용자가 지금 SSE 로 붙어 있는지 (채팅 상대 '접속 중' 표시) */
    public boolean online(long userId) {
        for (Subscriber s : subscribers) {
            if (s.scope().userId() == userId) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return subscribers.size();
    }

    /**
     * @param viewType 지금 보는 범위 종류 (site · factory · process, 없으면 null)
     * @param viewId   그 id
     * @param thermal  열화상 카메라(센서) id, 열화상 화면을 볼 때만
     */
    public record Subscriber(SseEmitter emitter, UserScope scope, String viewType, Long viewId, Long thermal) {

        static Subscriber of(SseEmitter emitter, UserScope scope, String view, Long thermal) {
            String type = null;
            Long id = null;
            if (view != null && view.contains(":")) {
                String[] p = view.split(":", 2);
                type = p[0];
                try {
                    id = Long.parseLong(p[1]);
                } catch (NumberFormatException ignored) {
                    type = null;
                }
            }
            return new Subscriber(emitter, scope, type, id, thermal);
        }

        public boolean viewingProcess(long processId) {
            return "process".equals(viewType) && viewId != null && viewId == processId;
        }
    }
}
