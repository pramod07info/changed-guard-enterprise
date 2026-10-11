package com.changeguard.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import com.changeguard.security.TestJwtConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.changeguard.dto.OpenApiRequest;
import com.changeguard.dto.SavedAnalysis;
import com.changeguard.dto.SnapshotResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestJwtConfiguration.class)
class AnalysisHistoryHttpTest {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    private final String service = "http-test-" + UUID.randomUUID();
    private final HttpClient client = HttpClient.newHttpClient();
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/api/v1" + path); }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer test-token").GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM analysis_report WHERE service = ?", service);
        jdbc.update("DELETE FROM contract_snapshot WHERE service = ?", service);
    }
    @Test void createdLocationAndReadEndpoints() throws Exception {
        var document = Map.<String, Object>of("openapi", "3.0.3", "paths", Map.of());
        String body = json.writeValueAsString(new OpenApiRequest(service, document, document, null));
        var created = client.send(HttpRequest.newBuilder(uri("/analyses")).header("Authorization", "Bearer test-token").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(201, created.statusCode());
        var saved = json.readValue(created.body(), SavedAnalysis.class);
        assertEquals("/api/v1/analyses/" + saved.id(), created.headers().firstValue("Location").orElseThrow());
        assertEquals(200, get("/analyses/" + saved.id()).statusCode());
        var snapshot = get("/snapshots/" + saved.beforeSnapshotId());
        assertEquals(200, snapshot.statusCode());
        assertEquals(document, json.readValue(snapshot.body(), SnapshotResponse.class).document());
        var listed = get("/analyses?service=" + service);
        assertEquals(200, listed.statusCode());
        assertTrue(listed.body().contains(saved.id().toString()));
    }
    @Test void notFoundAndBadRequestErrors() throws Exception {
        assertEquals(404, get("/analyses/" + UUID.randomUUID()).statusCode());
        assertEquals(404, get("/snapshots/" + UUID.randomUUID()).statusCode());
        assertEquals(400, get("/analyses/not-a-uuid").statusCode());
        assertEquals(400, get("/analyses?service=" + service + "&limit=101").statusCode());
        assertEquals(400, get("/analyses").statusCode());
    }
}
