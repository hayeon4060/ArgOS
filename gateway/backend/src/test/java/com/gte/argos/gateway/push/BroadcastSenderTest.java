package com.gte.argos.gateway.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 방송 시스템 REST 호출: 경로 · 본문 · 헤더 · 재시도 */
class BroadcastSenderTest {

    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    private String serve(int... statuses) throws Exception {
        AtomicInteger n = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " key="
                    + ex.getRequestHeaders().getFirst("X-API-Key") + " " + body);
            int i = n.getAndIncrement();
            int status = i < statuses.length ? statuses[i] : 200;
            byte[] res = "{\"status\":\"ON\"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, res.length);
            ex.getResponseBody().write(res);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/";
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static BroadcastSender.Start fire3() {
        return new BroadcastSender.Start(3, 1, 5L, 12L, 31L, "5공장 · 도장 공정 · 상도 건조로 버너실",
                "도장 공정에서 화재가 발생했습니다. 작업자는 즉시 대피하십시오.",
                OffsetDateTime.parse("2026-11-16T09:30:00+09:00"));
    }

    @Test
    void startAndStopCallBroadcastSystem() throws Exception {
        BroadcastSender sender = new BroadcastSender(serve(), "k-123", 2000);

        assertThat(sender.start(fire3())).isTrue();
        assertThat(sender.stop(3, "CLEARED")).isTrue();

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).startsWith("POST /api/start key=k-123 ")
                .contains("\"requestId\":\"fire-3\"", "\"type\":\"FIRE\"", "\"plantId\":1", "\"factoryId\":5",
                        "\"processId\":12", "\"equipmentId\":31", "\"repeat\":true", "상도 건조로 버너실",
                        "\"detectedAt\":\"2026-11-16T09:30:00+09:00\"");
        assertThat(calls.get(1)).startsWith("POST /api/stop key=k-123 ")
                .contains("\"requestId\":\"fire-3\"", "\"reason\":\"CLEARED\"");
    }

    @Test
    void startRetriesOnceThenFails() throws Exception {
        BroadcastSender sender = new BroadcastSender(serve(500, 503), "", 2000);

        assertThat(sender.start(fire3())).isFalse();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).contains("key=null");
    }

    @Test
    void startSucceedsOnRetry() throws Exception {
        BroadcastSender sender = new BroadcastSender(serve(500), "", 2000);

        assertThat(sender.start(fire3())).isTrue();
        assertThat(calls).hasSize(2);
    }

    @Test
    void withoutUrlOnlyLogs() {
        BroadcastSender sender = new BroadcastSender("", "", 2000);

        assertThat(sender.start(fire3())).isTrue();
        assertThat(sender.stop(3, "AUTO_CLEARED")).isTrue();
    }
}
