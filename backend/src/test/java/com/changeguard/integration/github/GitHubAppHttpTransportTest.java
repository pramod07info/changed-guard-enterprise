package com.changeguard.integration.github;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.exception.GitHubIntegrationException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GitHubAppHttpTransportTest {
  final JsonMapper json = JsonMapper.builder().build();

  GitHubAppHttpTransport transport(HttpServer server) {
    return new GitHubAppHttpTransport(
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
        json);
  }

  @Test
  void writesJsonAndAuthOnlyToFixedOrigin() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var auth = new java.util.concurrent.atomic.AtomicReference<String>();
    var payload = new java.util.concurrent.atomic.AtomicReference<String>();
    server.createContext(
        "/repos/team/payments/check-runs",
        exchange -> {
          auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
          payload.set(new String(exchange.getRequestBody().readAllBytes()));
          byte[] body = "{}".getBytes();
          exchange.sendResponseHeaders(201, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var transport = transport(server);
      assertArrayEquals(
          "{}".getBytes(),
          transport.request(
              "POST", "/repos/team/payments/check-runs", "fixture", Map.of("name", "test")));
      assertEquals("Bearer fixture", auth.get());
      assertEquals("test", json.readTree(payload.get()).path("name").asText());
      for (String path :
          new String[] {
            "https://evil.test/repos/a/b", "//evil.test/repos/a/b", "/repos/a/../b", "/login"
          })
        assertThrows(
            IllegalArgumentException.class, () -> transport.request("GET", path, "fixture", null));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void neverFollowsRedirectsAndBoundsBodies() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var followed = new AtomicBoolean();
    server.createContext(
        "/repos/redirect",
        exchange -> {
          exchange.getResponseHeaders().add("Location", "/repos/target");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    server.createContext(
        "/repos/target",
        exchange -> {
          followed.set(true);
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.createContext(
        "/repos/large",
        exchange -> {
          byte[] body = new byte[2 * 1024 * 1024 + 1];
          exchange.sendResponseHeaders(200, body.length);
          try {
            exchange.getResponseBody().write(body);
          } catch (java.io.IOException ignored) {
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      var transport = transport(server);
      assertThrows(
          GitHubIntegrationException.class,
          () -> transport.request("GET", "/repos/redirect", "fixture", null));
      assertFalse(followed.get());
      assertThrows(
          GitHubIntegrationException.class,
          () -> transport.request("GET", "/repos/large", "fixture", null));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rateLimitsRetryAndDeniedCredentialsStayTerminal() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/repos/limited",
        exchange -> {
          exchange.getResponseHeaders().add("X-RateLimit-Remaining", "0");
          exchange.sendResponseHeaders(403, -1);
          exchange.close();
        });
    server.createContext(
        "/repos/denied",
        exchange -> {
          exchange.sendResponseHeaders(401, -1);
          exchange.close();
        });
    server.start();
    try {
      var transport = transport(server);
      assertEquals(
          503,
          assertThrows(
                  GitHubIntegrationException.class,
                  () -> transport.request("GET", "/repos/limited", "fixture", null))
              .status()
              .value());
      assertEquals(
          422,
          assertThrows(
                  GitHubIntegrationException.class,
                  () -> transport.request("GET", "/repos/denied", "fixture", null))
              .status()
              .value());
    } finally {
      server.stop(0);
    }
  }
}
