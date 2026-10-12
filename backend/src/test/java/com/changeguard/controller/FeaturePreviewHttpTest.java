package com.changeguard.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.dto.*;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "changeguard.features.analysis-history.enabled=false",
          "changeguard.features.database-history.enabled=false",
      "changeguard.features.dependency-analysis.enabled=false",
          "changeguard.features.test-recommendations.enabled=false"
    })
@Import(DatabaseSchemaHttpTest.Fixtures.class)
class FeaturePreviewHttpTest {
  @LocalServerPort int port;
  @Autowired JsonMapper json;

  HttpResponse<String> post(String path, Object body) throws Exception {
    return HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer schema-owner")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void previewsContinueWithHistoryAndEnrichmentsDisabled() throws Exception {
    var document = Map.<String, Object>of("openapi", "3.0.3", "paths", Map.of());
    var result =
        post("/impact/openapi", new OpenApiRequest("payments", document, document, Map.of()));
    assertEquals(200, result.statusCode(), result.body());
    assertTrue(json.readValue(result.body(), OpenApiReport.class).recommendedTests().isEmpty());
    assertEquals(503, post("/analyses", null).statusCode());
    var schema = Map.<String, Object>of("dialect", "postgresql", "tables", Map.of());
    result =
        post("/impact/database", new DatabaseSchemaRequest("payments", schema, schema, Map.of()));
    assertEquals(200, result.statusCode(), result.body());
    assertEquals(503, post("/database-analyses", null).statusCode());
  }
}
