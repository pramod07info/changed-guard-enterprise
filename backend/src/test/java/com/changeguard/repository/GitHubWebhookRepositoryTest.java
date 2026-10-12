package com.changeguard.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.entity.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GitHubWebhookRepositoryTest {
  @Autowired CatalogRepository catalog;
  @Autowired BaselineRepository baselines;
  @Autowired GitHubWebhookRepository webhooks;
  final String tenant = "webhook-repository-" + UUID.randomUUID();

  GitHubWebhookSubscription subscription() {
    var project =
        new CatalogProject(UUID.randomUUID(), tenant, "project", "publisher", Instant.now());
    catalog.insertProject(project);
    var service =
        new CatalogServiceRecord(
            UUID.randomUUID(),
            tenant,
            project.id(),
            "payments",
            "team",
            "https://github.com/team/payments",
            Instant.now());
    catalog.insertService(service);
    var baseline =
        new ContractBaseline(
            UUID.randomUUID(),
            tenant,
            service.id(),
            service.name(),
            "1.0",
            "production",
            "{}",
            "publisher",
            Instant.now());
    baselines.insert(baseline);
    var sub =
        new GitHubWebhookSubscription(
            UUID.randomUUID(),
            tenant,
            baseline.id(),
            "team/payments",
            "openapi.json",
            "{}",
            "publisher");
    webhooks.insert(sub);
    return sub;
  }

  @Test
  void tenantReadsCannotExposeOtherSubscriptionsOrJobs() {
    var s = subscription();
    var delivery = UUID.randomUUID();
    assertTrue(webhooks.enqueue(s, delivery, "a".repeat(64), 7, "b".repeat(40)));
    assertFalse(webhooks.enqueue(s, delivery, "a".repeat(64), 7, "b".repeat(40)));
    assertTrue(webhooks.find("other", s.id()).isEmpty());
    assertTrue(webhooks.list("other", s.baselineId()).isEmpty());
    assertTrue(webhooks.delivery("other", s.id(), delivery).isEmpty());
    assertTrue(webhooks.hash("other", s.id(), delivery).isEmpty());
    assertEquals("QUEUED", webhooks.delivery(tenant, s.id(), delivery).orElseThrow().state());
  }

  @Test
  void databaseRejectsCrossTenantBaselineBinding() {
    var s = subscription();
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            webhooks.insert(
                new GitHubWebhookSubscription(
                    UUID.randomUUID(),
                    "other",
                    s.baselineId(),
                    s.repository(),
                    "other.json",
                    "{}",
                    "publisher")));
  }

  @Test
  void databaseRejectsCrossTenantDeliveryBinding() {
    var s = subscription();
    var forged =
        new GitHubWebhookSubscription(
            s.id(), "other", s.baselineId(), s.repository(), s.contractPath(), "{}", "publisher");
    assertThrows(
        DataIntegrityViolationException.class,
        () -> webhooks.enqueue(forged, UUID.randomUUID(), "a".repeat(64), 7, "b".repeat(40)));
  }
}
