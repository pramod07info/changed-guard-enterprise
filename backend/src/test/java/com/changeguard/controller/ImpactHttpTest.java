package com.changeguard.controller;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import com.changeguard.security.TestJwtConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestJwtConfiguration.class)
class ImpactHttpTest {
    @LocalServerPort int port;

    private HttpResponse<String> post(String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/impact"))
                .header("Authorization", "Bearer test-token").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    @Test void validRequest() throws Exception {
        var response = post("""
                {"service":"payments","before":{"GET /payments":{"id":"string"}},"after":{}}
                """);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ENDPOINT_REMOVED"));
        assertTrue(response.body().contains("HIGH"));
    }
    @Test void malformedAndMissingContract() throws Exception {
        assertEquals(400, post("{").statusCode());
        assertEquals(400, post("{\"service\":\"payments\"}").statusCode());
        assertEquals(400, post("null").statusCode());
    }
    @Test void health() throws Exception {
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("UP"));
    }

    @Test void openApiEndpointAndUnsupportedVersion() throws Exception {
        String body = """
                {"service":"orders","before":{"openapi":"3.0.3","paths":{"/orders":{"get":{"responses":{"200":{"description":"OK"}}}}}},
                "after":{"openapi":"3.0.3","paths":{}}}
                """;
        var client = HttpClient.newHttpClient();
        var uri = URI.create("http://127.0.0.1:" + port + "/api/v1/impact/openapi");
        var response = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer test-token").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("OPERATION_REMOVED"));
        var invalid = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer test-token").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.replace("3.0.3", "3.1.0"))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalid.statusCode());
        assertTrue(invalid.body().contains("Only OpenAPI"));
    }
}
