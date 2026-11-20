package com.gte.argos.gateway.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
public class AuthTokens {
    private static final String ISSUER = "argos-gateway";
    private final SecretKey key;
    private final JwtEncoder encoder;
    private final SecureRandom random = new SecureRandom();
    private final long accessSeconds;
    private final long sessionSeconds;

    public AuthTokens(@Value("${argos.auth.jwt-secret-base64}") String secret,
            @Value("${argos.auth.access-seconds}") long accessSeconds,
            @Value("${argos.auth.session-seconds}") long sessionSeconds) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("GATEWAY_JWT_SECRET_BASE64 must be Base64", ex);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("GATEWAY_JWT_SECRET_BASE64 needs at least 32 random bytes");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.accessSeconds = accessSeconds;
        this.sessionSeconds = sessionSeconds;
    }

    public JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    public String accessToken(long userId, long sessionId, Instant sessionExpiry) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessSeconds);
        if (expiry.isAfter(sessionExpiry)) expiry = sessionExpiry;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER).issuedAt(now).expiresAt(expiry)
                .subject(String.valueOf(userId)).claim("sessionId", sessionId).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public long accessSeconds() { return accessSeconds; }
    public long sessionSeconds() { return sessionSeconds; }
}
