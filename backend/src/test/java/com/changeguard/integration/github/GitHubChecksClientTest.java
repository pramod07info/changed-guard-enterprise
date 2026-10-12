package com.changeguard.integration.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.changeguard.config.GitHubAppSettings;
import com.changeguard.entity.GitHubCheckJob;
import com.changeguard.exception.GitHubIntegrationException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

class GitHubChecksClientTest {
  final JsonMapper json = JsonMapper.builder().build();
  final UUID subscription = UUID.randomUUID(), delivery = UUID.randomUUID();
  final GitHubCheckJob job =
      new GitHubCheckJob(
          "tenant",
          subscription,
          delivery,
          7,
          "team/payments",
          "b".repeat(40),
          "SUCCEEDED",
          UUID.randomUUID(),
          "{}",
          1,
          UUID.randomUUID());
  final GitHubAppSettings settings =
      new GitHubAppSettings(true, 42, "{\"" + subscription + "\":7}", true, json);

  class Fixture implements GitHubAppTransport {
    final List<String> methods = new ArrayList<>();
    Map<String, Object> mintBody, checkBody;
    Map<String, Object> run = null;
    boolean wrongInstall, wrongHead, wrongApp, ambiguousCreate, expired, overflow;

    @Override
    public byte[] request(String method, String path, String bearer, Map<String, Object> body) {
      methods.add(method + " " + path);
      if (path.endsWith("/installation")) {
        assertEquals("app-jwt", bearer);
        return bytes(Map.of("id", wrongInstall ? 8 : 7, "app_id", 42));
      }
      if (path.equals("/repos/team/payments")) {
        assertNull(bearer);
        return bytes(Map.of("id", 123, "full_name", "team/payments", "private", false));
      }
      if (path.endsWith("/access_tokens")) {
        mintBody = body;
        assertEquals("app-jwt", bearer);
        return bytes(
            Map.of(
                "token",
                "installation-fixture",
                "expires_at",
                Instant.now().plusSeconds(expired ? -1 : 3600).toString(),
                "permissions",
                Map.of("checks", "write")));
      }
      assertEquals("installation-fixture", bearer);
      if (method.equals("GET"))
        return bytes(
            Map.of(
                "total_count",
                overflow ? 101 : (run == null ? 0 : 1),
                "check_runs",
                run == null ? List.of() : List.of(run)));
      checkBody = body;
      run = new HashMap<>(body);
      run.put("head_sha", wrongHead ? "a".repeat(40) : job.headSha());
      run.put("id", 99);
      run.put("app", Map.of("id", wrongApp ? 999 : 42));
      if (ambiguousCreate) {
        ambiguousCreate = false;
        throw new GitHubIntegrationException(
            HttpStatus.SERVICE_UNAVAILABLE, "fixture timeout after create");
      }
      return bytes(run);
    }

    byte[] bytes(Object value) {
      return json.writeValueAsBytes(value);
    }
  }

  GitHubChecksClient client(Fixture fixture) {
    var signer = mock(GitHubAppJwt.class);
    when(signer.token()).thenReturn("app-jwt");
    return new GitHubChecksClient(settings, signer, fixture, json);
  }

  @Test
  void createsCommitBoundCheckWithRepositoryAndChecksOnlyToken() {
    var fixture = new Fixture();
    assertEquals(99, client(fixture).publish(job, "success", "fixture"));
    assertEquals(Map.of("checks", "write"), fixture.mintBody.get("permissions"));
    assertEquals(List.of(123L), fixture.mintBody.get("repository_ids"));
    assertEquals(job.headSha(), fixture.checkBody.get("head_sha"));
    assertEquals(job.externalId(), fixture.checkBody.get("external_id"));
    assertEquals(GitHubAppSettings.checkName(subscription), fixture.checkBody.get("name"));
  }

  @Test
  void ambiguousCreateIsReconciledAndPatchedWithoutAnotherPost() {
    var fixture = new Fixture();
    fixture.ambiguousCreate = true;
    var client = client(fixture);
    assertThrows(GitHubIntegrationException.class, () -> client.publish(job, "failure", "fixture"));
    assertEquals(99, client.publish(job, "failure", "fixture"));
    assertEquals(
        1,
        fixture.methods.stream()
            .filter(s -> s.equals("POST /repos/team/payments/check-runs"))
            .count());
    assertTrue(fixture.methods.contains("PATCH /repos/team/payments/check-runs/99"));
    assertFalse(fixture.checkBody.containsKey("head_sha"));
  }

  @Test
  void wrongInstallationCannotMintOrWrite() {
    var fixture = new Fixture();
    fixture.wrongInstall = true;
    assertThrows(
        GitHubIntegrationException.class, () -> client(fixture).publish(job, "success", "fixture"));
    assertNull(fixture.mintBody);
    assertNull(fixture.checkBody);
  }

  @Test
  void unapprovedSubscriptionDoesNotMakeAnyRequests() {
    var fixture = new Fixture();
    var other =
        new GitHubCheckJob(
            "tenant",
            UUID.randomUUID(),
            delivery,
            7,
            job.repository(),
            job.headSha(),
            job.deliveryState(),
            job.analysisId(),
            "{}",
            1,
            UUID.randomUUID());
    assertThrows(
        GitHubIntegrationException.class,
        () -> client(fixture).publish(other, "success", "fixture"));
    assertTrue(fixture.methods.isEmpty());
  }

  @Test
  void wrongCommitOrAppResponseDoesNotCountAsSuccess() {
    for (boolean wrongApp : new boolean[] {false, true}) {
      var fixture = new Fixture();
      fixture.wrongHead = !wrongApp;
      fixture.wrongApp = wrongApp;
      assertThrows(
          GitHubIntegrationException.class,
          () -> client(fixture).publish(job, "success", "fixture"));
    }
  }

  @Test
  void expiredTokenAndIncompleteReconciliationFailClosed() {
    for (boolean expired : new boolean[] {false, true}) {
      var fixture = new Fixture();
      fixture.expired = expired;
      fixture.overflow = !expired;
      assertThrows(
          GitHubIntegrationException.class,
          () -> client(fixture).publish(job, "failure", "fixture"));
      assertNull(fixture.checkBody);
    }
  }
}
