package com.changeguard.impact;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ImpactHttpTest {
    @LocalServerPort int port;

    private HttpResponse<String> post(String body) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/impact"))
                .header("Content-Type", "application/json")
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
}
