package com.changeguard.repository;

import com.changeguard.config.GitHubAppSettings;
import com.changeguard.dto.GitHubCheckStatus;
import com.changeguard.entity.GitHubCheckJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GitHubCheckRepository {
  private final JdbcTemplate jdbc;

  public GitHubCheckRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  // Trusted operator-approved subscription identity; no tenant-facing global lookup.
  public void seed(UUID subscription, long installation) {
    jdbc.update(
        """
INSERT INTO github_check_publication (tenant_id,subscription_id,delivery_id,installation_id)
SELECT tenant_id,subscription_id,delivery_id,? FROM github_webhook_delivery
WHERE subscription_id=? AND state IN ('SUCCEEDED','FAILED','SUPERSEDED')
ON CONFLICT (subscription_id,delivery_id) DO NOTHING
""",
        installation,
        subscription);
  }

  public Optional<GitHubCheckJob> claim() {
    UUID token = UUID.randomUUID();
    return jdbc
        .query(
            """
WITH next AS (
    SELECT subscription_id,delivery_id FROM github_check_publication
    WHERE state IN ('QUEUED','PROCESSING') AND attempts<3 AND available_at<=now()
    ORDER BY available_at FOR UPDATE SKIP LOCKED LIMIT 1
), claimed AS (
    UPDATE github_check_publication p SET state='PROCESSING',attempts=p.attempts+1,
        lease_token=?,available_at=now()+interval '300 seconds',updated_at=now()
    FROM next n WHERE p.subscription_id=n.subscription_id AND p.delivery_id=n.delivery_id RETURNING p.*
) SELECT c.*,s.repository,d.head_sha,d.state AS delivery_state,d.analysis_id,a.report::text AS report_json
FROM claimed c JOIN github_webhook_subscription s ON s.tenant_id=c.tenant_id AND s.id=c.subscription_id
JOIN github_webhook_delivery d ON d.tenant_id=c.tenant_id AND d.subscription_id=c.subscription_id AND d.delivery_id=c.delivery_id
LEFT JOIN analysis_report a ON a.tenant_id=c.tenant_id AND a.id=d.analysis_id
""",
            (rs, row) ->
                new GitHubCheckJob(
                    rs.getString("tenant_id"),
                    rs.getObject("subscription_id", UUID.class),
                    rs.getObject("delivery_id", UUID.class),
                    rs.getLong("installation_id"),
                    rs.getString("repository"),
                    rs.getString("head_sha"),
                    rs.getString("delivery_state"),
                    rs.getObject("analysis_id", UUID.class),
                    rs.getString("report_json"),
                    rs.getInt("attempts"),
                    token),
            token)
        .stream()
        .findFirst();
  }

  public boolean owned(GitHubCheckJob j) {
    return jdbc.queryForObject(
            "SELECT count(*) FROM github_check_publication WHERE tenant_id=? AND subscription_id=?"
                + " AND delivery_id=? AND state='PROCESSING' AND lease_token=? AND"
                + " available_at>now()",
            Integer.class,
            j.tenantId(),
            j.subscriptionId(),
            j.deliveryId(),
            j.leaseToken())
        == 1;
  }

  public void complete(GitHubCheckJob j, long runId) {
    jdbc.update(
        "UPDATE github_check_publication SET"
            + " state='PUBLISHED',check_run_id=?,error_code=NULL,lease_token=NULL,updated_at=now()"
            + " WHERE tenant_id=? AND subscription_id=? AND delivery_id=? AND state='PROCESSING'"
            + " AND lease_token=?",
        runId,
        j.tenantId(),
        j.subscriptionId(),
        j.deliveryId(),
        j.leaseToken());
  }

  public void fail(GitHubCheckJob j, String error, boolean retry) {
    jdbc.update(
        "UPDATE github_check_publication SET"
            + " state=?,error_code=?,lease_token=NULL,available_at=now()+interval '60"
            + " seconds',updated_at=now() WHERE tenant_id=? AND subscription_id=? AND delivery_id=?"
            + " AND state='PROCESSING' AND lease_token=?",
        retry && j.attempts() < 3 ? "QUEUED" : "FAILED",
        error,
        j.tenantId(),
        j.subscriptionId(),
        j.deliveryId(),
        j.leaseToken());
  }

  public void expireExhausted() {
    jdbc.update(
        "UPDATE github_check_publication SET"
            + " state='FAILED',error_code='CHECK_LEASE_EXHAUSTED',lease_token=NULL,updated_at=now()"
            + " WHERE state='PROCESSING' AND attempts=3 AND available_at<=now()");
  }

  public Optional<GitHubCheckStatus> status(String tenant, UUID subscription, UUID delivery) {
    return jdbc
        .query(
            "SELECT * FROM github_check_publication WHERE tenant_id=? AND subscription_id=? AND"
                + " delivery_id=?",
            (rs, row) ->
                new GitHubCheckStatus(
                    rs.getString("state"),
                    rs.getInt("attempts"),
                    rs.getObject("check_run_id", Long.class),
                    GitHubAppSettings.checkName(subscription),
                    rs.getString("error_code")),
            tenant,
            subscription,
            delivery)
        .stream()
        .findFirst();
  }

  public boolean retry(String tenant, UUID subscription, UUID delivery, long installation) {
    return jdbc.update(
            "UPDATE github_check_publication SET"
                + " state='QUEUED',attempts=0,error_code=NULL,lease_token=NULL,available_at=now(),updated_at=now()"
                + " WHERE tenant_id=? AND subscription_id=? AND delivery_id=? AND installation_id=?"
                + " AND state='FAILED'",
            tenant,
            subscription,
            delivery,
            installation)
        == 1;
  }
}
