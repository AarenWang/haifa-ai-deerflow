package org.wrj.haifa.ai.utilitymcp.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.wrj.haifa.ai.utilitymcp.config.UtilityMcpProperties;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityErrorCode;
import org.wrj.haifa.ai.utilitymcp.mcp.UtilityToolException;

class ResilientJsonPostProviderTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void postsJsonAndRetriesOneTransientFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/query", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = calls.incrementAndGet() == 1 ? 503 : 200;
            byte[] body = (status == 200 ? "{\"vulns\":[]}" : "unavailable").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", status == 200 ? "application/json" : "text/plain");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ProviderPayload payload = provider(4096).post("/v1/query", Map.of(
                "version", "1.0.0", "package", Map.of("ecosystem", "Maven", "name", "org.example:demo")));

        assertThat(calls).hasValue(2);
        assertThat(payload.body().path("vulns").isArray()).isTrue();
        assertThat(requestBody.get()).contains("\"ecosystem\":\"Maven\"").contains("org.example:demo");
    }

    @Test
    void rejectsWrongMimeAndOversizedResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/wrong", exchange -> {
            byte[] body = "<html/>".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/large", exchange -> {
            byte[] body = ("{\"value\":\"" + "x".repeat(2_000) + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ResilientJsonPostProvider provider = provider(1024);
        assertThatThrownBy(() -> provider.post("/wrong", Map.of()))
                .isInstanceOfSatisfying(UtilityToolException.class,
                        ex -> assertThat(ex.code()).isEqualTo(UtilityErrorCode.UPSTREAM_UNAVAILABLE));
        assertThatThrownBy(() -> provider.post("/large", Map.of()))
                .isInstanceOfSatisfying(UtilityToolException.class,
                        ex -> assertThat(ex.code()).isEqualTo(UtilityErrorCode.RESULT_TOO_LARGE));
    }

    private ResilientJsonPostProvider provider(int maxBytes) {
        UtilityMcpProperties.Provider properties = new UtilityMcpProperties.Provider(
                "http://127.0.0.1:" + server.getAddress().getPort());
        properties.setAllowHttpForTests(true);
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setResponseTimeout(Duration.ofSeconds(1));
        properties.setMaxResponseBytes(maxBytes);
        return new ResilientJsonPostProvider("fixture", properties, new ObjectMapper());
    }
}
