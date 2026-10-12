package com.changeguard.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.changeguard.dto.*;
import com.changeguard.entity.*;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.integration.github.GitHubAppJwt;
import com.changeguard.integration.github.GitHubChecksClient;
import com.changeguard.repository.*;
import com.changeguard.service.GitHubCheckPublisher;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "changeguard.github.enabled=true",
      "changeguard.github.webhooks.enabled=true",
      "changeguard.github.webhooks.secret=check-test-only-secret-32-characters",
      "changeguard.github.webhooks.worker-enabled=false",
      "changeguard.github.app.enabled=true",
      "changeguard.github.app.id=42",
      "changeguard.github.app.publisher-enabled=false"
    })
@Import(GitHubCheckHttpTest.Fixtures.class)
class GitHubCheckHttpTest {
  static final String TENANT = "checks-" + UUID.randomUUID();
  static final UUID SUB = UUID.randomUUID();

  @DynamicPropertySource
  static void approval(DynamicPropertyRegistry registry) {
    registry.add("changeguard.github.app.bindings", () -> "{\"" + SUB + "\":7}");
  }

  @TestConfiguration
  static class Fixtures {
    @Bean
    @Primary
    JwtDecoder checkDecoder() {
      return token -> {
        if (!token.equals("check-owner") && !token.equals("check-other"))
          throw new BadJwtException("Invalid fixture");
        return Jwt.withTokenValue(token)
            .header("alg", "fixture")
            .subject("publisher")
            .claim("tenant_id", token.equals("check-owner") ? TENANT : "other-check-tenant")
            .build();
      };
    }
  }

  @MockitoBean GitHubAppJwt signer;
  @MockitoBean GitHubChecksClient client;
  @LocalServerPort int port;
  @Autowired JsonMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired CatalogRepository catalog;
  @Autowired BaselineRepository baselines;
  @Autowired GitHubWebhookRepository webhooks;
  @Autowired AnalysisRepository analyses;
  @Autowired ContractSnapshotRepository snapshots;
  @Autowired GitHubCheckRepository checks;
  @Autowired GitHubCheckPublisher publisher;
  @Autowired PlatformTransactionManager manager;
  final HttpClient http = HttpClient.newHttpClient();
  UUID delivery;

  UUID prepare(String state, boolean review, UUID sub) {
    var project =
        new CatalogProject(UUID.randomUUID(), TENANT, "project", "publisher", Instant.now());
    catalog.insertProject(project);
    var service =
        new CatalogServiceRecord(
            UUID.randomUUID(),
            TENANT,
            project.id(),
            "payments",
            "team",
            "https://github.com/team/payments",
            Instant.now());
    catalog.insertService(service);
    var baseline =
        new ContractBaseline(
            UUID.randomUUID(),
            TENANT,
            service.id(),
            "payments",
            "1.0",
            "production",
            "{}",
            "publisher",
            Instant.now());
    baselines.insert(baseline);
    var subscription =
        new GitHubWebhookSubscription(
            sub, TENANT, baseline.id(), "team/payments", "openapi.json", "{}", "publisher");
    webhooks.insert(subscription);
    delivery = UUID.randomUUID();
    webhooks.enqueue(subscription, delivery, "a".repeat(64), 7, "b".repeat(40));
    UUID reportId = null;
    if (state.equals("SUCCEEDED")) {
      UUID before = UUID.randomUUID(), after = UUID.randomUUID();
      reportId = UUID.randomUUID();
      snapshots.insert(new ContractSnapshot(before, TENANT, "payments", Instant.now(), "{}"));
      snapshots.insert(new ContractSnapshot(after, TENANT, "payments", Instant.now(), "{}"));
      var report =
          new OpenApiReport(
              "payments",
              review ? ImpactReport.Risk.HIGH : ImpactReport.Risk.NONE,
              List.of(),
              List.of(),
              List.of(),
              review,
              List.of());
      analyses.insert(
          new AnalysisRecord(
              reportId,
              TENANT,
              "payments",
              Instant.now(),
              before,
              after,
              json.writeValueAsString(report),
              "{}",
              baseline.id()));
    }
    jdbc.update(
        "UPDATE github_webhook_delivery SET state=?,analysis_id=? WHERE tenant_id=? AND"
            + " subscription_id=? AND delivery_id=?",
        state,
        reportId,
        TENANT,
        sub,
        delivery);
    publisher.enqueueCompleted();
    return reportId;
  }

  GitHubCheckJob claim() {
    return new TransactionTemplate(manager).execute(status -> checks.claim().orElseThrow());
  }

  GitHubCheckStatus state() {
    return checks.status(TENANT, SUB, delivery).orElseThrow();
  }

  HttpResponse<String> request(String token, String method, String suffix) throws Exception {
    var b =
        HttpRequest.newBuilder(
            URI.create(
                "http://127.0.0.1:"
                    + port
                    + "/api/v1/github/webhooks/"
                    + SUB
                    + "/deliveries/"
                    + delivery
                    + "/check"
                    + suffix));
    if (token != null) b.header("Authorization", "Bearer " + token);
    return http.send(
        b.method(method, HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  void ready() {
    jdbc.update(
        "UPDATE github_check_publication SET available_at=now()-interval '1 second' WHERE"
            + " tenant_id=?",
        TENANT);
  }

  @AfterEach
  void cleanup() {
    for (String table :
        new String[] {
          "github_check_publication",
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
  void publishesSuccessWithoutDuplicatingAnalysisAndProtectsStatus() throws Exception {
    UUID saved = prepare("SUCCEEDED", false, SUB);
    when(client.publish(any(), eq("success"), anyString())).thenReturn(99L);
    publisher.enqueueCompleted();
    publisher.process(claim());
    assertEquals("PUBLISHED", state().state());
    assertEquals(99L, state().checkRunId());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM analysis_report WHERE tenant_id=?", Integer.class, TENANT));
    assertNotNull(saved);
    assertEquals(200, request("check-owner", "GET", "").statusCode());
    assertEquals(404, request("check-other", "GET", "").statusCode());
    assertEquals(401, request(null, "GET", "").statusCode());
    assertEquals(401, request(null, "POST", "/retry").statusCode());
    assertEquals(404, request("check-other", "POST", "/retry").statusCode());
    assertEquals(409, request("check-owner", "POST", "/retry").statusCode());
    assertTrue(checks.claim().isEmpty());
  }

  @Test
  void reviewRequiredPublishesFailure() {
    prepare("SUCCEEDED", true, SUB);
    when(client.publish(any(), eq("failure"), anyString())).thenReturn(99L);
    publisher.process(claim());
    verify(client).publish(any(), eq("failure"), contains("Risk: HIGH"));
    assertEquals("PUBLISHED", state().state());
  }

  @Test
  void terminalAnalysisFailureCannotPassCheck() {
    prepare("FAILED", false, SUB);
    when(client.publish(any(), eq("failure"), anyString())).thenReturn(99L);
    publisher.process(claim());
    verify(client).publish(any(), eq("failure"), contains("No compatibility approval"));
    assertEquals("PUBLISHED", state().state());
  }

  @Test
  void supersededAnalysisCannotPassCheck() {
    prepare("SUPERSEDED", false, SUB);
    when(client.publish(any(), eq("failure"), anyString())).thenReturn(99L);
    publisher.process(claim());
    verify(client).publish(any(), eq("failure"), anyString());
  }

  @Test
  void unapprovedSubscriptionNeverEnqueuesPublication() {
    prepare("FAILED", false, UUID.randomUUID());
    assertTrue(checks.claim().isEmpty());
    verifyNoInteractions(client);
  }

  @Test
  void retriesPublicationOnlyAndAllowsTenantToRetryAfterExhaustion() throws Exception {
    prepare("SUCCEEDED", false, SUB);
    when(client.publish(any(), anyString(), anyString()))
        .thenThrow(new GitHubIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, "fixture"));
    for (int i = 1; i <= 3; i++) {
      publisher.process(claim());
      assertEquals(i, state().attempts());
      ready();
    }
    assertEquals("FAILED", state().state());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM analysis_report WHERE tenant_id=?", Integer.class, TENANT));
    assertEquals(202, request("check-owner", "POST", "/retry").statusCode());
    assertEquals("QUEUED", state().state());
    assertEquals(0, state().attempts());
    doReturn(99L).when(client).publish(any(), anyString(), anyString());
    publisher.process(claim());
    assertEquals("PUBLISHED", state().state());
  }

  @Test
  void rejectedCredentialsStopWithoutThreeAttempts() {
    prepare("FAILED", false, SUB);
    when(client.publish(any(), anyString(), anyString()))
        .thenThrow(new GitHubIntegrationException(HttpStatus.UNPROCESSABLE_ENTITY, "fixture"));
    publisher.process(claim());
    assertEquals("FAILED", state().state());
    assertEquals(1, state().attempts());
    assertTrue(checks.claim().isEmpty());
  }

  @Test
  void expiredOwnerCannotMakeWritesAndThirdCrashLeaseExhausts() {
    prepare("FAILED", false, SUB);
    var old = claim();
    ready();
    var current = claim();
    publisher.process(old);
    verifyNoInteractions(client);
    ready();
    claim();
    ready();
    checks.expireExhausted();
    assertEquals("FAILED", state().state());
    assertTrue(checks.claim().isEmpty());
  }
}
