package com.gte.argos.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gte.argos.gateway.auth.AuthRepository;
import com.gte.argos.gateway.auth.AuthTokens;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthTokens tokens,
            AuthRepository repository, ObjectMapper mapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/api/v1/system/info",
                                "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        // API 명세서 (Swagger UI · OpenAPI JSON)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/me", "/api/v1/me/password",
                                "/api/v1/auth/logout", "/api/v1/auth/extend").authenticated()
                        .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                                authentication.get().getAuthorities().stream()
                                        .anyMatch(authority -> authority.getAuthority().startsWith("ROLE_"))
                                        && authentication.get().getAuthorities().stream()
                                        .noneMatch(authority -> "PASSWORD_RESET_REQUIRED"
                                                .equals(authority.getAuthority())))))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(tokens.decoder())
                                .jwtAuthenticationConverter(token -> {
                                    long userId = Long.parseLong(token.getSubject());
                                    long sessionId = ((Number) token.getClaim("sessionId")).longValue();
                                    AuthRepository.User user = repository.findActiveUser(userId);
                                    if (user == null || !repository.isSessionActive(sessionId, userId)) {
                                        throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
                                                "AUTH_REQUIRED");
                                    }
                                    var authorities = new ArrayList<SimpleGrantedAuthority>();
                                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role()));
                                    if (user.pwResetRequired()) {
                                        authorities.add(new SimpleGrantedAuthority("PASSWORD_RESET_REQUIRED"));
                                    }
                                    return new JwtAuthenticationToken(token, authorities,
                                            String.valueOf(user.id()));
                                })))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, mapper, 401,
                                        ex.getMessage() != null && ex.getMessage().contains("expired")
                                                ? "TOKEN_EXPIRED" : "AUTH_REQUIRED"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, mapper, 403, "FORBIDDEN")))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper,
            int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                Map.of("success", false, "error", Map.of("code", code, "message", "인증을 확인해 주세요.")));
    }
}

