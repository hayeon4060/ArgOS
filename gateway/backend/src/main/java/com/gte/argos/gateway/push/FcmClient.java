package com.gte.argos.gateway.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * FCM HTTP v1 전송 (라이브러리 없이). Firebase 서비스 계정 JSON 으로 구글 OAuth 토큰을 받아
 * {@code POST https://fcm.googleapis.com/v1/projects/{projectId}/messages:send} 를 부른다. 토큰은 50분 재사용.
 */
public class FcmClient {

    static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final RestClient rest = RestClient.create();
    private final ObjectMapper mapper;
    private final String projectId;
    private final String clientEmail;
    private final String tokenUri;
    private final PrivateKey key;
    private String accessToken;
    private Instant accessUntil = Instant.EPOCH;

    @SuppressWarnings("unchecked")
    public FcmClient(String credentialsPath, ObjectMapper mapper) throws Exception {
        this.mapper = mapper;
        Map<String, Object> json = mapper.readValue(Files.readString(Path.of(credentialsPath)), Map.class);
        this.projectId = String.valueOf(json.get("project_id"));
        this.clientEmail = String.valueOf(json.get("client_email"));
        this.tokenUri = String.valueOf(json.getOrDefault("token_uri", "https://oauth2.googleapis.com/token"));
        this.key = privateKey(String.valueOf(json.get("private_key")));
    }

    /** 앱(phone/src/push.js)이 만드는 알림 채널 — 중요도 최대(화면 위로 뜨는 알림 + 소리) */
    static final String ANDROID_CHANNEL = "argos_alert";

    /** 종류별 알림 문구 { 제목, 내용 } — 관제 웹 개발 서버 시연 알림(mockSync.js)과 같은 말투 */
    private static final Map<String, String[]> TEXT = Map.of(
            "CHECKLIST_ASSIGNED", new String[] {"새 점검 체크리스트가 도착했습니다", "눌러서 확인하세요"},
            "CHECKLIST_REMIND", new String[] {"다시 알림 · 체크리스트를 확인해 주세요", "눌러서 확인하세요"},
            "CHECKLIST_REASSIGNED", new String[] {"체크리스트 담당자가 바뀌었습니다", "더 이상 내 체크리스트가 아닙니다"},
            "WORKER_CALL", new String[] {"공장 담당자 호출", "현장을 확인해 주세요"},
            "FIRE", new String[] {"화재 발생", "안내에 따라 바로 대피하세요"},
            "FIRE_CLEARED", new String[] {"화재 해제", "화재 상황이 해제되었습니다"});

    /** 알림 + data 메시지 전송. 실패하면 예외 */
    public void send(String token, Map<String, String> data) {
        rest.post().uri("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send")
                .header("Authorization", "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("message", message(token, data))).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
    }

    /**
     * FCM 메시지 본문. data 만 보내면 앱이 꺼져 있거나 화면이 꺼져 있을 때 Android 가 알림을 띄우지 않으므로
     * notification(제목 · 내용 · 채널)을 같이 넣음. 알림을 누르면 data(checklistId 등)가 앱으로 감
     */
    static Map<String, Object> message(String token, Map<String, String> data) {
        String[] text = TEXT.getOrDefault(data.get("type"), new String[] {"ArgOS 알림", "눌러서 확인하세요"});
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("token", token);
        message.put("notification", Map.of("title", text[0], "body", text[1]));
        message.put("data", data);
        message.put("android", Map.of("priority", "high", "notification", Map.of("channel_id", ANDROID_CHANNEL)));
        return message;
    }

    private synchronized String accessToken() {
        if (accessToken != null && Instant.now().isBefore(accessUntil)) {
            return accessToken;
        }
        String assertion = jwt(clientEmail, tokenUri, Instant.now());
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + assertion;
        Map<String, Object> res = rest.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
        accessToken = String.valueOf(res.get("access_token"));
        accessUntil = Instant.now().plusSeconds(50 * 60);
        return accessToken;
    }

    /** 서비스 계정 JWT (RS256) */
    String jwt(String issuer, String audience, Instant now) {
        try {
            Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
            String header = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", issuer);
            claims.put("scope", SCOPE);
            claims.put("aud", audience);
            claims.put("iat", now.getEpochSecond());
            claims.put("exp", now.getEpochSecond() + 3600);
            String payload = b64.encodeToString(mapper.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key);
            sig.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
            return header + "." + payload + "." + b64.encodeToString(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException("FCM 인증 토큰을 만들지 못했습니다", e);
        }
    }

    static PrivateKey privateKey(String pem) throws Exception {
        String body = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
    }
}
