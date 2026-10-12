package com.changeguard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.changeguard.config.FeatureFlags;
import com.changeguard.dto.FeatureManifest;
import com.changeguard.integration.github.GitHubPrClient;
import com.changeguard.repository.*;
import com.changeguard.service.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "changeguard.features.normalized-analysis.enabled=false",
      "changeguard.features.open-api-analysis.enabled=false",
      "changeguard.features.catalog.enabled=false",
      "changeguard.features.database-analysis.enabled=false",
      "changeguard.features.metrics.enabled=false",
      "changeguard.github.enabled=true",
      "changeguard.github.webhooks.enabled=true",
      "changeguard.github.app.enabled=true"
    })
@Import(DatabaseSchemaHttpTest.Fixtures.class)
class FeatureFlagsHttpTest {
  @LocalServerPort int port;
  @Autowired JsonMapper json;
  @Autowired FeatureFlags flags;
  @Autowired GitHubWebhookWorker worker;
  @Autowired GitHubCheckPublisher publisher;
  @MockitoBean GitHubPrClient github;
  @MockitoBean CatalogRepository catalog;
  @MockitoBean BaselineRepository baselines;
  @MockitoBean AnalysisRepository analyses;
  @MockitoBean DatabaseAnalysisRepository databases;
  @MockitoBean GitHubWebhookRepository jobs;
  @MockitoBean GitHubCheckRepository checks;

  HttpResponse<String> call(String path, boolean authenticated, String method) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Content-Type", "application/json");
    if (authenticated) request.header("Authorization", "Bearer schema-owner");
    return HttpClient.newHttpClient()
        .send(
            request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void disabledRoutesNeverReachRepositoriesAndStillRequireAuthentication() throws Exception {
    for (String path :
        new String[] {
          "/api/v1/impact",
          "/api/v1/impact/openapi",
          "/api/v1/impact/database",
          "/api/v1/analyses",
          "/api/v1/snapshots/none",
          "/api/v1/database-analyses",
          "/api/v1/projects",
          "/api/v1/services/none/baselines",
          "/api/v1/baselines/none/preview",
          "/api/v1/baselines/none/github-pr/analyses",
          "/api/v1/github/webhooks/none/deliveries",
          "/actuator/prometheus",
          "/actuator/info"
        }) {
      assertEquals(401, call(path, false, "GET").statusCode(), path);
      var result = call(path, true, "GET");
      assertEquals(503, result.statusCode(), result.body());
      assertTrue(result.body().contains("Feature disabled"));
    }
    assertEquals(503, call("/api/v1/analyses/", true, "POST").statusCode());
    assertEquals(503, call("/api/v1/%61nalyses", true, "POST").statusCode());
    // Signed ingress remains bearer-exempt but is also disabled before signature/queue work.
    assertEquals(503, call("/api/v1/github/webhooks/none/deliveries", false, "POST").statusCode());
    verifyNoInteractions(github, catalog, baselines, analyses, databases, jobs, checks);
  }

  @Test
  void sessionHealthAndManifestRemainAvailable() throws Exception {
    assertEquals(200, call("/actuator/health", false, "GET").statusCode());
    assertEquals(200, call("/api/v1/session", true, "GET").statusCode());
    assertEquals(401, call("/api/v1/features", false, "GET").statusCode());
    var result = call("/api/v1/features", true, "GET");
    assertEquals(200, result.statusCode());
    assertEquals(flags.manifest(), json.readValue(result.body(), FeatureManifest.class));
    assertFalse(result.body().contains("secret"));
  }

  @Test
  void disabledWorkersDoNotClaimOrMutateQueuedWork() {
    worker.poll();
    worker.process(null);
    publisher.enqueueCompleted();
    publisher.poll();
    publisher.process(null);
    verifyNoInteractions(jobs, checks, github, analyses, baselines, catalog);
  }
}
