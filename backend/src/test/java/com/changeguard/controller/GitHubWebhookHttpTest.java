package com.changeguard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.changeguard.dto.*;
import com.changeguard.entity.GitHubWebhookJob;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.integration.github.GitHubPrClient;
import com.changeguard.repository.GitHubWebhookRepository;
import com.changeguard.service.GitHubWebhookSignatureTest;
import com.changeguard.service.GitHubWebhookWorker;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "changeguard.github.enabled=true",
      "changeguard.github.webhooks.enabled=true",
      "changeguard.github.webhooks.secret=webhook-test-only-secret-32-characters",
      "changeguard.github.webhooks.worker-enabled=false"
    })
@Import(GitHubWebhookHttpTest.Fixtures.class)
class GitHubWebhookHttpTest {
  static final String TENANT = "webhooks-" + UUID.randomUUID();

  @TestConfiguration
  static class Fixtures {
    @Bean
    @Primary
    JwtDecoder webhookDecoder() {
      return token -> {
        if (!token.equals("owner") && !token.equals("other"))
          throw new BadJwtException("Invalid fixture");
        return Jwt.withTokenValue(token)
            .header("alg", "fixture")
            .subject("publisher")
            .claim("tenant_id", token.equals("owner") ? TENANT : "other-webhooks")
            .build();
      };
    }
  }

  @MockitoBean GitHubPrClient github;
  @LocalServerPort int port;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean GitHubWebhookRepository jobs;
  @Autowired GitHubWebhookWorker worker;
  @Autowired PlatformTransactionManager manager;
  final HttpClient client = HttpClient.newHttpClient();
  final Map<String, Object> document = Map.of("openapi", "3.0.3", "paths", Map.of());

  record Setup(UUID baseline, UUID subscription) {}

  HttpResponse<String> api(String token, String method, String path, Object body) throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
            .header("Content-Type", "application/json");
    if (token != null) b.header("Authorization", "Bearer " + token);
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  Setup setup() throws Exception {
    var project =
        json.readValue(
            api("owner", "POST", "/projects", new ProjectRequest("Payments")).body(),
            ProjectResponse.class);
    var service =
        json.readValue(
            api(
                    "owner",
                    "POST",
                    "/projects/" + project.id() + "/services",
                    new CatalogServiceRequest(
                        "payments", "team", "https://github.com/team/payments"))
                .body(),
            CatalogServiceResponse.class);
    var baseline =
        json.readValue(
            api(
                    "owner",
                    "POST",
                    "/services/" + service.id() + "/baselines",
                    new BaselineRequest("1.0", "production", document))
                .body(),
            BaselineResponse.class);
    var result =
        api(
            "owner",
            "POST",
            "/baselines/" + baseline.id() + "/github-webhooks",
            new GitHubWebhookRequest("openapi.json", Map.of()));
    assertEquals(201, result.statusCode(), result.body());
    return new Setup(
        baseline.id(), json.readValue(result.body(), GitHubWebhookResponse.class).id());
  }

  byte[] payload(String repo, String sha) {
    return json.writeValueAsBytes(
        Map.of(
            "action",
            "synchronize",
            "number",
            7,
            "repository",
            Map.of("full_name", repo),
            "pull_request",
            Map.of(
                "number",
                7,
                "state",
                "open",
                "base",
                Map.of("repo", Map.of("full_name", repo)),
                "head",
                Map.of("sha", sha))));
  }

  HttpResponse<String> deliver(
      UUID sub, UUID delivery, String event, byte[] bytes, String signature) throws Exception {
    var b =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:" + port + "/api/v1/github/webhooks/" + sub + "/deliveries"))
            .header("Content-Type", "application/json")
            .header("X-GitHub-Delivery", delivery.toString())
            .header("X-GitHub-Event", event);
    if (signature != null) b.header("X-Hub-Signature-256", signature);
    return client.send(
        b.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> signed(Setup s, UUID id, byte[] bytes) throws Exception {
    return deliver(
        s.subscription(), id, "pull_request", bytes, GitHubWebhookSignatureTest.sign(bytes));
  }

  GitHubWebhookJob claim() {
    return new TransactionTemplate(manager).execute(status -> jobs.claim().orElseThrow());
  }

  void candidate(String sha, Map<String, Object> doc) {
    var source =
        new GitHubSource(
            "team/payments",
            "team/payments",
            7,
            "https://github.com/team/payments/pull/7",
            "a".repeat(40),
            sha,
            "openapi.json",
            "c".repeat(40),
            Instant.now());
    when(github.fetch(any(), eq(7), eq("openapi.json")))
        .thenReturn(new GitHubPrClient.Candidate(source, doc));
  }

  GitHubWebhookDelivery state(Setup s, UUID id) {
    return jobs.delivery(TENANT, s.subscription(), id).orElseThrow();
  }

  int reports() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM analysis_report WHERE tenant_id=?", Integer.class, TENANT);
  }

  void ready() {
    jdbc.update(
        "UPDATE github_webhook_delivery SET available_at=now()-interval '1 second' WHERE"
            + " tenant_id=?",
        TENANT);
  }

  @AfterEach
  void cleanup() {
    for (String table :
        new String[] {
          "github_webhook_delivery",
          "github_webhook_subscription",
          "analysis_report",
          "contract_snapshot",
          "contract_baseline",
          "catalog_service",
          "catalog_project"
        }) jdbc.update("DELETE FROM " + table + " WHERE tenant_id=?", TENANT);
  }

  @Test
  void signatureIsMandatoryAndApiRoutesStayProtected() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    var body = payload("team/payments", "b".repeat(40));
    assertEquals(401, deliver(s.subscription(), id, "pull_request", body, null).statusCode());
    assertEquals(
        401,
        deliver(s.subscription(), id, "pull_request", body, "sha256=" + "0".repeat(64))
            .statusCode());
    assertEquals(
        401,
        api(null, "GET", "/baselines/" + s.baseline() + "/github-webhooks", null).statusCode());
    assertEquals(
        404,
        api("other", "GET", "/baselines/" + s.baseline() + "/github-webhooks", null).statusCode());
    assertEquals(
        401,
        api(
                null,
                "POST",
                "/baselines/" + s.baseline() + "/github-webhooks",
                new GitHubWebhookRequest("openapi.json", Map.of()))
            .statusCode());
    verifyNoInteractions(github);
    assertEquals(0, reports());
  }

  @Test
  void queuesDuplicateOnceAndProcessesWithoutBrowserIdentity() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    var body = payload("team/payments", "b".repeat(40));
    assertEquals(202, signed(s, id, body).statusCode());
    assertTrue(signed(s, id, body).body().contains("duplicate"));
    verifyNoInteractions(github);
    candidate("b".repeat(40), document);
    worker.process(claim());
    assertEquals("SUCCEEDED", state(s, id).state());
    assertEquals(1, reports());
    assertEquals(
        200, api("owner", "GET", "/analyses/" + state(s, id).analysisId(), null).statusCode());
    assertEquals(
        404,
        api("other", "GET", "/github/webhooks/" + s.subscription() + "/deliveries/" + id, null)
            .statusCode());
    assertEquals(
        401,
        api(null, "GET", "/github/webhooks/" + s.subscription() + "/deliveries/" + id, null)
            .statusCode());
    assertTrue(jobs.claim().isEmpty());
  }

  @Test
  void rejectsWrongRepositoryChangedDeliveryAndOversizedBodies() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    var body = payload("team/payments", "b".repeat(40));
    assertEquals(400, signed(s, id, payload("attacker/repo", "b".repeat(40))).statusCode());
    assertEquals(202, signed(s, id, body).statusCode());
    assertEquals(409, signed(s, id, payload("team/payments", "d".repeat(40))).statusCode());
    assertEquals(
        413, deliver(s.subscription(), id, "pull_request", new byte[1048577], null).statusCode());
    verifyNoInteractions(github);
  }

  @Test
  void ignoredAndPingEventsDoNotQueueOrFetch() throws Exception {
    var s = setup();
    byte[] bytes = "{}".getBytes();
    assertTrue(
        deliver(
                s.subscription(),
                UUID.randomUUID(),
                "ping",
                bytes,
                GitHubWebhookSignatureTest.sign(bytes))
            .body()
            .contains("pong"));
    assertTrue(
        deliver(
                s.subscription(),
                UUID.randomUUID(),
                "push",
                bytes,
                GitHubWebhookSignatureTest.sign(bytes))
            .body()
            .contains("ignored"));
    assertTrue(jobs.claim().isEmpty());
    verifyNoInteractions(github);
  }

  @Test
  void staleHeadDoesNotSaveAnotherCommitsEvidence() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    signed(s, id, payload("team/payments", "b".repeat(40)));
    candidate("d".repeat(40), document);
    worker.process(claim());
    assertEquals("SUPERSEDED", state(s, id).state());
    assertEquals(0, reports());
  }

  @Test
  void transientErrorsRetryThreeTimesThenStop() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    signed(s, id, payload("team/payments", "b".repeat(40)));
    when(github.fetch(any(), anyInt(), anyString()))
        .thenThrow(new GitHubIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, "fixture"));
    for (int attempt = 1; attempt <= 3; attempt++) {
      worker.process(claim());
      assertEquals(attempt, state(s, id).attempts());
      ready();
    }
    assertEquals("FAILED", state(s, id).state());
    assertTrue(jobs.claim().isEmpty());
    assertEquals(0, reports());
  }

  @Test
  void invalidContractIsTerminalAndWritesNoSnapshots() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    signed(s, id, payload("team/payments", "b".repeat(40)));
    candidate("b".repeat(40), Map.of("openapi", "2.0", "paths", Map.of()));
    worker.process(claim());
    assertEquals("FAILED", state(s, id).state());
    assertEquals(0, reports());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM contract_snapshot WHERE tenant_id=?", Integer.class, TENANT));
  }

  @Test
  void completionFailureRollsBackSavedEvidence() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    signed(s, id, payload("team/payments", "b".repeat(40)));
    candidate("b".repeat(40), document);
    doThrow(new IllegalStateException("fixture completion failure"))
        .when(jobs)
        .finish(any(), eq("SUCCEEDED"), any(), isNull());
    worker.process(claim());
    assertEquals("QUEUED", state(s, id).state());
    assertEquals(0, reports());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT count(*) FROM contract_snapshot WHERE tenant_id=?", Integer.class, TENANT));
  }

  @Test
  void registrationRejectsDuplicatesInvalidPathsAndForeignBaselines() throws Exception {
    var s = setup();
    String path = "/baselines/" + s.baseline() + "/github-webhooks";
    assertEquals(
        409,
        api("owner", "POST", path, new GitHubWebhookRequest("openapi.json", Map.of()))
            .statusCode());
    assertEquals(
        400,
        api("owner", "POST", path, new GitHubWebhookRequest("../openapi.json", Map.of()))
            .statusCode());
    assertEquals(
        404,
        api("other", "POST", path, new GitHubWebhookRequest("other.json", Map.of())).statusCode());
    verifyNoInteractions(github);
  }

  @Test
  void expiredLeaseReclaimsAndOldWorkerCannotSave() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    signed(s, id, payload("team/payments", "b".repeat(40)));
    var old = claim();
    assertTrue(jobs.claim().isEmpty());
    ready();
    var current = claim();
    candidate("b".repeat(40), document);
    worker.process(old);
    assertEquals(0, reports());
    worker.process(current);
    assertEquals(1, reports());
    assertEquals("SUCCEEDED", state(s, id).state());
  }

  @Test
  void exhaustedCrashLeaseBecomesFailed() throws Exception {
    var s = setup();
    var id = UUID.randomUUID();
    signed(s, id, payload("team/payments", "b".repeat(40)));
    claim();
    ready();
    claim();
    ready();
    claim();
    ready();
    jobs.expireExhausted();
    assertEquals("FAILED", state(s, id).state());
    assertTrue(jobs.claim().isEmpty());
  }

  @Test
  void recentDeliveriesArePaginatedAndTenantProtected() throws Exception {
    var s = setup();
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    signed(s, first, payload("team/payments", "b".repeat(40)));
    signed(s, second, payload("team/payments", "d".repeat(40)));
    String path = "/github/webhooks/" + s.subscription() + "/deliveries";
    var page =
        json.readValue(
            api("owner", "GET", path + "?limit=1&offset=0", null).body(),
            GitHubWebhookDeliverySummary[].class);
    var next =
        json.readValue(
            api("owner", "GET", path + "?limit=1&offset=1", null).body(),
            GitHubWebhookDeliverySummary[].class);
    assertEquals(1, page.length);
    assertEquals(1, next.length);
    assertNotEquals(page[0].deliveryId(), next[0].deliveryId());
    assertEquals(7, page[0].pullNumber());
    assertEquals("QUEUED", page[0].state());
    assertNotNull(page[0].createdAt());
    assertNull(page[0].check());
    assertEquals(
        0,
        json.readValue(
                api("owner", "GET", path + "?offset=2", null).body(),
                GitHubWebhookDeliverySummary[].class)
            .length);
    assertEquals(404, api("other", "GET", path, null).statusCode());
    assertEquals(401, api(null, "GET", path, null).statusCode());
    assertEquals(400, api("owner", "GET", path + "?limit=0", null).statusCode());
    assertEquals(
        404,
        api("owner", "GET", "/github/webhooks/" + UUID.randomUUID() + "/deliveries", null)
            .statusCode());
  }
}
