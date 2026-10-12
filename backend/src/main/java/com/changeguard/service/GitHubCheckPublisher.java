package com.changeguard.service;

import com.changeguard.config.GitHubAppSettings;
import com.changeguard.dto.OpenApiReport;
import com.changeguard.entity.GitHubCheckJob;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.integration.github.GitHubChecksClient;
import com.changeguard.repository.GitHubCheckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GitHubCheckPublisher {
  private static final Logger LOG = LoggerFactory.getLogger(GitHubCheckPublisher.class);
  private final GitHubAppSettings settings;
  private final GitHubCheckRepository checks;
  private final GitHubChecksClient client;
  private final TransactionTemplate tx;
  private final JsonMapper json;
  private final boolean polling;

  public GitHubCheckPublisher(
      GitHubAppSettings settings,
      GitHubCheckRepository checks,
      GitHubChecksClient client,
      PlatformTransactionManager manager,
      JsonMapper json,
      @Value("${changeguard.github.app.publisher-enabled:true}") boolean polling) {
    this.settings = settings;
    this.checks = checks;
    this.client = client;
    this.tx = new TransactionTemplate(manager);
    this.json = json;
    this.polling = polling;
  }

  public void enqueueCompleted() {
    if (!settings.enabled()) return;
    settings.bindings().forEach(checks::seed);
  }

  @Scheduled(fixedDelayString = "${changeguard.github.app.poll-ms:5000}")
  public void poll() {
    if (!settings.enabled() || !polling) return;
    try {
      enqueueCompleted();
      checks.expireExhausted();
      var claimed = tx.execute(status -> checks.claim());
      if (claimed != null) claimed.ifPresent(this::process);
    } catch (RuntimeException e) {
      LOG.error("GitHub check polling failed ({})", e.getClass().getSimpleName());
    }
  }

  public void process(GitHubCheckJob job) {
    if (!settings.approved(job.subscriptionId(), job.installationId())) {
      checks.fail(job, "CHECK_BINDING_REMOVED", false);
      return;
    }
    if (!checks.owned(job)) return;
    try {
      String conclusion = "failure";
      String summary =
          "Analysis did not complete successfully ("
              + job.deliveryState()
              + "). No compatibility approval was issued.";
      if ("SUCCEEDED".equals(job.deliveryState())) {
        var report = json.readValue(job.reportJson(), OpenApiReport.class);
        if (report == null
            || job.analysisId() == null
            || report.risk() == null
            || report.breakingChanges() == null
            || report.potentialConsumers() == null)
          throw new IllegalStateException("Missing saved report");
        conclusion =
            report.requiresReview()
                    || report.risk() == com.changeguard.dto.ImpactReport.Risk.HIGH
                    || !report.breakingChanges().isEmpty()
                ? "failure"
                : "success";
        summary =
            "Risk: "
                + report.risk()
                + ". Supported breaking changes: "
                + report.breakingChanges().size()
                + ". Potential consumers: "
                + report.potentialConsumers().size()
                + ". Saved analysis: "
                + job.analysisId()
                + ". This check covers the supported OpenAPI rules and supplied dependency graph;"
                + " it is not a full API, DB or Kafka guarantee.";
      }
      long id =
          client.publish(job, conclusion, summary); // Never hold database locks during GitHub I/O.
      checks.complete(job, id);
    } catch (GitHubIntegrationException e) {
      checks.fail(
          job,
          "GITHUB_CHECK_UNAVAILABLE",
          e.status().is5xxServerError() || e.status().value() == 429);
    } catch (RuntimeException e) {
      LOG.error(
          "Check publication failed for delivery {} ({})",
          job.deliveryId(),
          e.getClass().getSimpleName());
      checks.fail(job, "CHECK_PUBLICATION_FAILED", true);
    }
  }
}
