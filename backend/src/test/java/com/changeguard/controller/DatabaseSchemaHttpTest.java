package com.changeguard.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.dto.*;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(DatabaseSchemaHttpTest.Fixtures.class)
class DatabaseSchemaHttpTest {
  static final String TENANT = "schema-http-" + UUID.randomUUID();

  @TestConfiguration
  static class Fixtures {
    @Bean
    @Primary
    JwtDecoder schemaDecoder() {
      return token -> {
        if (!token.equals("schema-owner") && !token.equals("schema-other"))
          throw new BadJwtException("fixture");
        return Jwt.withTokenValue(token)
            .header("alg", "fixture")
            .subject("publisher")
            .claim("tenant_id", token.equals("schema-owner") ? TENANT : "other-schema")
            .build();
      };
    }
  }

  @LocalServerPort int port;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate jdbc;
  final HttpClient client = HttpClient.newHttpClient();
  final Map<String, Object> before =
      Map.of(
          "dialect",
          "postgresql",
          "tables",
          Map.of(
              "public.payments",
              Map.of("columns", Map.of("id", Map.of("type", "uuid", "nullable", false)))));
  final Map<String, Object> after = Map.of("dialect", "postgresql", "tables", Map.of());

  HttpResponse<String> call(String token, String method, String path, String body)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
            .header("Content-Type", "application/json");
    if (token != null) b.header("Authorization", "Bearer " + token);
    return client.send(
        b.method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  String input() {
    return json.writeValueAsString(new DatabaseSchemaRequest("payments", before, after, Map.of()));
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM database_schema_analysis WHERE tenant_id=?", TENANT);
  }

  @Test
  void previewDoesNotPersistAndSavePreservesSchemaEvidence() throws Exception {
    var preview = call("schema-owner", "POST", "/impact/database", input());
    assertEquals(200, preview.statusCode(), preview.body());
    assertTrue(preview.body().contains("TABLE_REMOVED"));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM database_schema_analysis WHERE tenant_id=?",
            Integer.class,
            TENANT));
    var result = call("schema-owner", "POST", "/database-analyses", input());
    assertEquals(201, result.statusCode(), result.body());
    var saved = json.readValue(result.body(), SavedDatabaseAnalysis.class);
    assertEquals(before, saved.before());
    assertEquals(after, saved.after());
    var loaded =
        json.readValue(
            call("schema-owner", "GET", "/database-analyses/" + saved.id(), null).body(),
            SavedDatabaseAnalysis.class);
    assertEquals(saved, loaded);
    var rows =
        json.readValue(
            call("schema-owner", "GET", "/database-analyses?service=payments&limit=1", null).body(),
            AnalysisSummary[].class);
    assertEquals(1, rows.length);
    assertEquals(saved.id(), rows[0].id());
    assertEquals(
        0,
        json.readValue(
                call("schema-owner", "GET", "/database-analyses?service=payments&offset=1", null)
                    .body(),
                AnalysisSummary[].class)
            .length);
  }

  @Test
  void tenantAndAnonymousBoundariesArePreserved() throws Exception {
    assertEquals(401, call(null, "POST", "/impact/database", input()).statusCode());
    assertEquals(401, call(null, "POST", "/database-analyses", input()).statusCode());
    var saved =
        json.readValue(
            call("schema-owner", "POST", "/database-analyses", input()).body(),
            SavedDatabaseAnalysis.class);
    assertEquals(
        404, call("schema-other", "GET", "/database-analyses/" + saved.id(), null).statusCode());
    assertEquals(401, call(null, "GET", "/database-analyses/" + saved.id(), null).statusCode());
    assertEquals(
        0,
        json.readValue(
                call("schema-other", "GET", "/database-analyses?service=payments", null).body(),
                AnalysisSummary[].class)
            .length);
    assertEquals(
        404,
        call("schema-owner", "GET", "/database-analyses/" + UUID.randomUUID(), null).statusCode());
  }

  @Test
  void invalidAndOversizedRequestsDoNotSave() throws Exception {
    for (String value :
        new String[] {
          "null",
          "{",
          input() + "{}",
          input().replace("postgresql", "mysql"),
          json.writeValueAsString(
              Map.of("service", "payments", "tenantId", "other", "before", before, "after", after))
        })
      assertEquals(400, call("schema-owner", "POST", "/database-analyses", value).statusCode());
    assertEquals(
        413,
        call("schema-owner", "POST", "/database-analyses", " ".repeat(2 * 1048576 + 65537))
            .statusCode());
    assertEquals(
        400,
        call("schema-owner", "GET", "/database-analyses?service=payments&limit=0", null)
            .statusCode());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM database_schema_analysis WHERE tenant_id=?",
            Integer.class,
            TENANT));
  }
}
