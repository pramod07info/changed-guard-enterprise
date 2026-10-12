package com.changeguard.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.entity.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GitHubCheckRepositoryTest {
  @Autowired CatalogRepository catalog;
  @Autowired BaselineRepository baselines;
  @Autowired GitHubWebhookRepository webhooks;
  @Autowired GitHubCheckRepository checks;
  @Autowired JdbcTemplate jdbc;
  final String tenant = "check-repo-" + UUID.randomUUID();
  final UUID sub = UUID.randomUUID(), delivery = UUID.randomUUID();

  void prepare() {
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
            "payments",
            "1.0",
            "production",
            "{}",
            "publisher",
            Instant.now());
    baselines.insert(baseline);
    var subscription =
        new GitHubWebhookSubscription(
            sub, tenant, baseline.id(), "team/payments", "openapi.json", "{}", "publisher");
    webhooks.insert(subscription);
    webhooks.enqueue(subscription, delivery, "a".repeat(64), 7, "b".repeat(40));
    jdbc.update(
        "UPDATE github_webhook_delivery SET state='FAILED' WHERE tenant_id=? AND subscription_id=?"
            + " AND delivery_id=?",
        tenant,
        sub,
        delivery);
  }

  @Test
  void publicationReadsAndRetriesAreTenantScopedAndSeedingIsIdempotent() {
    prepare();
    checks.seed(sub, 7);
    checks.seed(sub, 7);
    assertEquals("QUEUED", checks.status(tenant, sub, delivery).orElseThrow().state());
    assertTrue(checks.status("other", sub, delivery).isEmpty());
    assertFalse(checks.retry("other", sub, delivery, 7));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM github_check_publication WHERE tenant_id=?",
            Integer.class,
            tenant));
  }

  @Test
  void databaseRejectsCrossTenantPublicationForExistingDelivery() {
    prepare();
    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            jdbc.update(
                "INSERT INTO github_check_publication"
                    + " (tenant_id,subscription_id,delivery_id,installation_id) VALUES"
                    + " ('other',?,?,7)",
                sub,
                delivery));
  }
}
