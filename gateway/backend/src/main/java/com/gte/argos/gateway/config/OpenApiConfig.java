package com.gte.argos.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 명세서 (springdoc). 보는 곳: /swagger-ui.html, 원본 JSON: /v3/api-docs.
 * 컨트롤러는 고치지 않고, 여기서 컨트롤러별 기본 태그(alarm-controller …)를 화면 이름으로 바꾼다.
 */
@Configuration
public class OpenApiConfig {
    static final String BEARER = "bearerAuth";

    /** 기본 태그 → 화면 이름 (앞 번호는 목록 순서용) */
    static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("auth-controller", "01 로그인 · 내 정보"),
            Map.entry("dashboard-controller", "02 대시보드 · 공정 현황"),
            Map.entry("alarm-controller", "03 알림"),
            Map.entry("equipment-action-controller", "04 설비 명령"),
            Map.entry("checklist-controller", "05 체크리스트"),
            Map.entry("fire-controller", "06 화재"),
            Map.entry("thermal-controller", "07 열화상"),
            Map.entry("threshold-controller", "08 기준값 관리"),
            Map.entry("stats-controller", "09 통계 · 이력"),
            Map.entry("report-controller", "10 월간 보고서"),
            Map.entry("inspection-controller", "11 점검 · OCR"),
            Map.entry("user-controller", "12 사용자 관리"),
            Map.entry("log-controller", "13 이용 로그"),
            Map.entry("factory-admin-controller", "14 공장 관리"),
            Map.entry("chat-controller", "15 채팅"),
            Map.entry("file-controller", "16 파일"),
            Map.entry("stream-controller", "17 실시간 (SSE)"),
            Map.entry("system-info-controller", "18 시스템"));

    /** 로그인 없이 부르는 API */
    static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/system/info");

    @Bean
    OpenAPI argosOpenApi() {
        return new OpenAPI()
                .info(new Info().title("ArgOS 연계 서버 API")
                        .version("v1")
                        .description("""
                                화면(웹·앱) → 연계 서버 API. 응답은 `{ success, data }` 형식.
                                1. `POST /api/v1/auth/login` 으로 로그인해서 `accessToken` 을 받는다.
                                2. 오른쪽 위 **Authorize** 에 토큰을 넣으면 나머지 API 를 부를 수 있다.
                                명세서와 다른 점은 `docs/API_IMPLEMENTED.md` 참고."""))
                .servers(List.of(new Server().url("/").description("이 서버")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    OpenApiCustomizer argosTags() {
        return openApi -> {
            if (openApi.getPaths() != null) {
                openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(op -> {
                    if (op.getTags() != null) {
                        op.setTags(op.getTags().stream().map(t -> TAGS.getOrDefault(t, t)).toList());
                    }
                    if (PUBLIC_PATHS.contains(path)) {
                        op.setSecurity(List.of());
                    }
                    fixFileUpload(op);
                }));
            }
            List<Tag> tags = new ArrayList<>();
            TAGS.values().stream().sorted().forEach(name -> tags.add(new Tag().name(name)));
            openApi.setTags(tags);
        };
    }

    /** @RequestParam MultipartFile 을 springdoc 이 JSON 본문으로 잡는 것을 multipart/form-data 로 바로잡는다 */
    @SuppressWarnings("rawtypes")
    static void fixFileUpload(Operation op) {
        if (op.getRequestBody() == null || op.getRequestBody().getContent() == null) {
            return;
        }
        Content content = op.getRequestBody().getContent();
        MediaType json = content.get("application/json");
        if (json == null || json.getSchema() == null || json.getSchema().getProperties() == null) {
            return;
        }
        Map<String, Schema> props = json.getSchema().getProperties();
        boolean hasFile = props.values().stream().anyMatch(p -> "binary".equals(p.getFormat()));
        if (hasFile) {
            content.remove("application/json");
            content.addMediaType("multipart/form-data", json);
        }
    }
}
