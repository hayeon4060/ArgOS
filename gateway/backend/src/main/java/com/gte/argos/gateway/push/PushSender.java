package com.gte.argos.gateway.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 앱 푸시 (FCM). 푸시는 구글 서버를 거치므로 설비 이름·센서값은 담지 않고 종류와 번호만 보낸다.
 *
 * <pre>
 * type: CHECKLIST_ASSIGNED · CHECKLIST_REMIND · CHECKLIST_REASSIGNED · WORKER_CALL · FIRE · FIRE_CLEARED
 * data: { "type": "...", "checklistId": "88" } 처럼 문자열만
 * </pre>
 *
 * argos.fcm.credentials (Firebase 서비스 계정 JSON 경로, 환경 변수 FCM_CREDENTIALS_PATH)가 있으면 FCM HTTP v1 로 실제 전송.
 * 없으면 로그만 남기고, 토큰이 있으면 성공으로 본다 (시연은 앱 화면을 직접 열어서 진행).
 */
@Component
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final FcmClient fcm;

    public PushSender(@Value("${argos.fcm.credentials:}") String credentials, ObjectMapper mapper) {
        FcmClient client = null;
        if (credentials != null && !credentials.isBlank()) {
            try {
                client = new FcmClient(credentials, mapper);
                log.info("FCM 실제 전송 켜짐");
            } catch (Exception e) {
                log.warn("FCM 서비스 계정을 읽지 못해 로그만 남깁니다: {}", e.getMessage());
            }
        }
        this.fcm = client;
    }

    /** @return 보냈으면 true, 토큰이 없거나 실패면 false */
    public boolean send(String fcmToken, String type, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.info("푸시 생략 (토큰 없음): type={} data={}", type, data);
            return false;
        }
        if (fcm == null) {
            log.info("푸시 전송(시뮬레이션): type={} data={} token={}…", type, data,
                    fcmToken.substring(0, Math.min(8, fcmToken.length())));
            return true;
        }
        try {
            java.util.Map<String, String> payload = new java.util.LinkedHashMap<>(data);
            payload.put("type", type);
            fcm.send(fcmToken, payload);
            return true;
        } catch (RuntimeException e) {
            log.warn("푸시 전송 실패 type={}: {}", type, e.getMessage());
            return false;
        }
    }
}
