package com.changeguard.integration.github;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class GitHubHttpTransportTest {
    private HttpServer server;
    private final AtomicReference<String> method = new AtomicReference<>();
    private GitHubHttpTransport transport(int status, byte[] body, boolean redirect) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/", exchange -> {
            method.set(exchange.getRequestMethod());
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            if (redirect) exchange.getResponseHeaders().add("Location", "http://untrusted.invalid/secret");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        return new GitHubHttpTransport(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }
    @AfterEach void stop() { if (server != null) server.stop(0); }
    @Test void transportUsesOnlyUnauthenticatedGet() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(body, transport(200, body, false).get("/repos/team/repo/pulls/1"));
        assertEquals("GET", method.get());
    }
    @Test void upstreamRateLimitIsServiceUnavailable() throws Exception {
        var failure = assertThrows(GitHubIntegrationException.class,
                () -> transport(403, "{}".getBytes(StandardCharsets.UTF_8), false).get("/repos/team/repo/pulls/1"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status());
    }
    @Test void missingResourceHasNotFoundError() throws Exception {
        var transport = transport(404, "{}".getBytes(StandardCharsets.UTF_8), false);
        assertThrows(ResourceNotFoundException.class, () -> transport.get("/repos/team/repo/pulls/1"));
    }
    @Test void redirectsAreRejectedInsteadOfFollowed() throws Exception {
        var transport = transport(302, "{}".getBytes(StandardCharsets.UTF_8), true);
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(GitHubIntegrationException.class,
                () -> transport.get("/repos/team/repo/pulls/1")).status());
    }
    @Test void oversizedResponsesAreCancelled() throws Exception {
        var transport = transport(200, new byte[2 * 1024 * 1024 + 1], false);
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(GitHubIntegrationException.class,
                () -> transport.get("/repos/team/repo/contents/openapi.json")).status());
    }
}
