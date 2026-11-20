package com.gte.argos.factory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.factory.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * /api/v1/internal/** 는 연계 서버만 부른다. argos.internal-api-key 가 있으면 X-Argos-Internal-Key 헤더를 확인한다.
 * 키가 비어 있으면 (로컬 개발) 검사하지 않는다.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Argos-Internal-Key";
    private static final Logger log = LoggerFactory.getLogger(InternalApiKeyFilter.class);

    private final byte[] key;
    private final ObjectMapper mapper;

    public InternalApiKeyFilter(ArgosProperties props, ObjectMapper mapper) {
        String k = props.internalApiKey();
        this.key = k == null || k.isBlank() ? null : k.getBytes(StandardCharsets.UTF_8);
        this.mapper = mapper;
        if (this.key == null) {
            log.warn("argos.internal-api-key 가 비어 있어 /api/v1/internal/** 를 키 없이 엽니다 (로컬 개발용)");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return key == null || !request.getRequestURI().startsWith("/api/v1/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        if (given != null && MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(), ApiResponse.fail("AUTH_REQUIRED", "내부 API 키가 맞지 않습니다."));
    }
}
