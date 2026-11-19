package com.gte.argos.gateway.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class FcmClientTest {

    @Test
    void 서비스_계정_키로_서명한_JWT() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(kp.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        assertThat(FcmClient.privateKey(pem).getAlgorithm()).isEqualTo("RSA");
        String[] parts = jwtParts(kp, pem);
        assertThat(parts.length).isEqualTo(3);
        Signature v = Signature.getInstance("SHA256withRSA");
        v.initVerify(kp.getPublic());
        v.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(v.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }

    private static String[] jwtParts(KeyPair kp, String pem) throws Exception {
        java.nio.file.Path f = java.nio.file.Files.createTempFile("sa", ".json");
        java.nio.file.Files.writeString(f, "{\"project_id\":\"argos\",\"client_email\":\"a@b.c\",\"private_key\":\""
                + pem.replace("\r", "").replace("\n", "\\n") + "\"}");
        FcmClient c = new FcmClient(f.toString(), new com.fasterxml.jackson.databind.ObjectMapper());
        return c.jwt("a@b.c", "https://oauth2.googleapis.com/token", java.time.Instant.parse("2026-11-16T00:00:00Z")).split("\\.");
    }
}
