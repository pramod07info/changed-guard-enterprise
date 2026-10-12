package com.changeguard.repository;

import com.changeguard.dto.GitHubWebhookDelivery;
import com.changeguard.entity.GitHubWebhookJob;
import com.changeguard.entity.GitHubWebhookSubscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class GitHubWebhookRepository {
  private final JdbcTemplate jdbc;
  private static final RowMapper<GitHubWebhookSubscription> SUB =
      (rs, row) ->
          new GitHubWebhookSubscription(
              rs.getObject("id", UUID.class),
              rs.getString("tenant_id"),
              rs.getObject("baseline_id", UUID.class),
              rs.getString("repository"),
              rs.getString("contract_path"),
              rs.getString("dependencies_json"),
              rs.getString("created_by"));

  public GitHubWebhookRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(GitHubWebhookSubscription s) {
    jdbc.update(
        "INSERT INTO github_webhook_subscription"
            + " (id,tenant_id,baseline_id,repository,contract_path,dependencies,created_by) VALUES"
            + " (?,?,?,?,?,CAST(? AS jsonb),?)",
        s.id(),
        s.tenantId(),
        s.baselineId(),
        s.repository(),
        s.contractPath(),
        s.dependenciesJson(),
        s.createdBy());
  }

  public Optional<GitHubWebhookSubscription> find(String tenant, UUID id) {
    return jdbc
        .query(
            "SELECT *, dependencies::text AS dependencies_json FROM github_webhook_subscription"
                + " WHERE tenant_id=? AND id=?",
            SUB,
            tenant,
            id)
        .stream()
        .findFirst();
  }

  // Ingress is already HMAC-authenticated. This is the only unscoped lookup, by opaque route ID.
  public Optional<GitHubWebhookSubscription> ingress(UUID id) {
    return jdbc
        .query(
            "SELECT *, dependencies::text AS dependencies_json FROM github_webhook_subscription"
                + " WHERE id=?",
            SUB,
            id)
        .stream()
        .findFirst();
  }

  public List<GitHubWebhookSubscription> list(String tenant, UUID baseline) {
    return jdbc.query(
        "SELECT *, dependencies::text AS dependencies_json FROM github_webhook_subscription WHERE"
            + " tenant_id=? AND baseline_id=? ORDER BY created_at DESC LIMIT 100",
        SUB,
        tenant,
        baseline);
  }

  public boolean enqueue(
      GitHubWebhookSubscription s, UUID delivery, String hash, int number, String sha) {
    return jdbc.update(
            """
INSERT INTO github_webhook_delivery (subscription_id,tenant_id,delivery_id,payload_hash,pull_number,head_sha)
VALUES (?,?,?,?,?,?) ON CONFLICT (subscription_id,delivery_id) DO NOTHING
""",
            s.id(),
            s.tenantId(),
            delivery,
            hash,
            number,
            sha)
        == 1;
  }

  public Optional<String> hash(String tenant, UUID subscription, UUID delivery) {
    return jdbc
        .query(
            "SELECT payload_hash FROM github_webhook_delivery WHERE tenant_id=? AND"
                + " subscription_id=? AND delivery_id=?",
            (rs, row) -> rs.getString(1),
            tenant,
            subscription,
            delivery)
        .stream()
        .findFirst();
  }

  public Optional<GitHubWebhookDelivery> delivery(String tenant, UUID subscription, UUID delivery) {
    return jdbc
        .query(
            "SELECT * FROM github_webhook_delivery WHERE tenant_id=? AND subscription_id=? AND"
                + " delivery_id=?",
            (rs, row) ->
                new GitHubWebhookDelivery(
                    delivery,
                    rs.getString("state"),
                    rs.getInt("attempts"),
                    rs.getObject("analysis_id", UUID.class),
                    rs.getString("error_code")),
            tenant,
            subscription,
            delivery)
        .stream()
        .findFirst();
  }

  public List<com.changeguard.dto.GitHubWebhookDeliverySummary> deliveries(
      String tenant, UUID subscription, int limit, int offset) {
    return jdbc.query(
        """
SELECT d.*, p.state AS check_state, p.attempts AS check_attempts,
    p.check_run_id, p.error_code AS check_error
FROM github_webhook_delivery d LEFT JOIN github_check_publication p
  ON p.tenant_id=d.tenant_id AND p.subscription_id=d.subscription_id AND p.delivery_id=d.delivery_id
WHERE d.tenant_id=? AND d.subscription_id=?
ORDER BY d.created_at DESC, d.delivery_id DESC LIMIT ? OFFSET ?
""",
        (rs, row) ->
            new com.changeguard.dto.GitHubWebhookDeliverySummary(
                rs.getObject("delivery_id", UUID.class),
                rs.getString("state"),
                rs.getInt("attempts"),
                rs.getObject("analysis_id", UUID.class),
                rs.getString("error_code"),
                rs.getInt("pull_number"),
                rs.getString("head_sha"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getString("check_state") == null
                    ? null
                    : new com.changeguard.dto.GitHubCheckStatus(
                        rs.getString("check_state"),
                        rs.getInt("check_attempts"),
                        rs.getObject("check_run_id", Long.class),
                        com.changeguard.config.GitHubAppSettings.checkName(subscription),
                        rs.getString("check_error"))),
        tenant,
        subscription,
        limit,
        offset);
  }

  // System worker scans a queue, never exposes an unscoped read API. FK carries tenant identity.
  public Optional<GitHubWebhookJob> claim() {
    UUID token = UUID.randomUUID();
    return jdbc
        .query(
            """
WITH next AS (
    SELECT subscription_id,delivery_id FROM github_webhook_delivery
    WHERE state IN ('QUEUED','PROCESSING') AND available_at <= now() AND attempts < 3
    ORDER BY available_at,created_at FOR UPDATE SKIP LOCKED LIMIT 1
), claimed AS (
    UPDATE github_webhook_delivery d SET state='PROCESSING',attempts=d.attempts+1,
        lease_token=?,available_at=now()+interval '120 seconds',updated_at=now()
    FROM next n WHERE d.subscription_id=n.subscription_id AND d.delivery_id=n.delivery_id RETURNING d.*
) SELECT c.*,s.id,s.baseline_id,s.repository,s.contract_path,s.dependencies::text AS dependencies_json,s.created_by
  FROM claimed c JOIN github_webhook_subscription s ON s.id=c.subscription_id AND s.tenant_id=c.tenant_id
""",
            (rs, row) ->
                new GitHubWebhookJob(
                    SUB.mapRow(rs, row),
                    rs.getObject("delivery_id", UUID.class),
                    rs.getString("head_sha"),
                    rs.getInt("pull_number"),
                    rs.getInt("attempts"),
                    token),
            token)
        .stream()
        .findFirst();
  }

  public boolean lockOwned(GitHubWebhookJob j) {
    return !jdbc.query(
            "SELECT 1 FROM github_webhook_delivery WHERE tenant_id=? AND subscription_id=? AND"
                + " delivery_id=? AND state='PROCESSING' AND lease_token=? FOR UPDATE",
            (rs, row) -> 1,
            j.subscription().tenantId(),
            j.subscription().id(),
            j.deliveryId(),
            j.leaseToken())
        .isEmpty();
  }

  public void finish(GitHubWebhookJob j, String state, UUID analysis, String error) {
    jdbc.update(
        "UPDATE github_webhook_delivery SET"
            + " state=?,analysis_id=?,error_code=?,lease_token=NULL,updated_at=now() WHERE"
            + " tenant_id=? AND subscription_id=? AND delivery_id=? AND state='PROCESSING' AND"
            + " lease_token=?",
        state,
        analysis,
        error,
        j.subscription().tenantId(),
        j.subscription().id(),
        j.deliveryId(),
        j.leaseToken());
  }

  public void fail(GitHubWebhookJob j, String code, boolean retry) {
    jdbc.update(
        """
UPDATE github_webhook_delivery SET state=?,error_code=?,lease_token=NULL,
    available_at=now()+interval '60 seconds',updated_at=now()
WHERE tenant_id=? AND subscription_id=? AND delivery_id=? AND state='PROCESSING' AND lease_token=?
""",
        retry && j.attempts() < 3 ? "QUEUED" : "FAILED",
        code,
        j.subscription().tenantId(),
        j.subscription().id(),
        j.deliveryId(),
        j.leaseToken());
  }

  public void expireExhausted() {
    jdbc.update(
        "UPDATE github_webhook_delivery SET"
            + " state='FAILED',error_code='LEASE_EXHAUSTED',lease_token=NULL,updated_at=now() WHERE"
            + " state='PROCESSING' AND attempts=3 AND available_at<=now()");
  }
}
