package com.changeguard.service;

import com.changeguard.dto.OpenApiRequest;
import com.changeguard.entity.GitHubWebhookJob;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.integration.github.GitHubPrClient;
import com.changeguard.integration.github.GitHubRepository;
import com.changeguard.repository.BaselineRepository;
import com.changeguard.repository.CatalogRepository;
import com.changeguard.repository.GitHubWebhookRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GitHubWebhookWorker {
  private static final Logger LOG = LoggerFactory.getLogger(GitHubWebhookWorker.class);
  private final GitHubWebhookRepository jobs;
  private final BaselineRepository baselines;
  private final CatalogRepository catalog;
  private final GitHubPrClient github;
  private final AnalysisHistoryService history;
  private final TransactionTemplate tx;
  private final JsonMapper json;
  private final boolean enabled;
  private final boolean polling;

  public GitHubWebhookWorker(
      GitHubWebhookRepository jobs,
      BaselineRepository baselines,
      CatalogRepository catalog,
      GitHubPrClient github,
      AnalysisHistoryService history,
      PlatformTransactionManager manager,
      JsonMapper json,
      com.changeguard.config.FeatureFlags flags,
      @Value("${changeguard.github.webhooks.worker-enabled:true}") boolean polling) {
    this.jobs = jobs;
    this.baselines = baselines;
    this.catalog = catalog;
    this.github = github;
    this.history = history;
    this.tx = new TransactionTemplate(manager);
    this.json = json;
    this.enabled = flags.manifest().webhooks();
    this.polling = polling;
  }

  @Scheduled(fixedDelayString = "${changeguard.github.webhooks.poll-ms:5000}")
  public void poll() {
    if (!enabled || !polling) return;
    try {
      jobs.expireExhausted();
      var claimed = tx.execute(status -> jobs.claim());
      if (claimed != null) claimed.ifPresent(this::process);
    } catch (RuntimeException e) {
      LOG.error("GitHub webhook worker failed ({})", e.getClass().getSimpleName());
    }
  }

  public void process(GitHubWebhookJob job) {
    if (!enabled) return;
    var s = job.subscription();
    try {
      var baseline =
          baselines
              .find(s.tenantId(), s.baselineId())
              .orElseThrow(() -> new InvalidContractException("Baseline unavailable"));
      var service =
          catalog
              .service(s.tenantId(), baseline.serviceId())
              .orElseThrow(() -> new InvalidContractException("Service unavailable"));
      var repo = GitHubRepository.fromCatalogUrl(service.repositoryUrl());
      if (!repo.fullName().equalsIgnoreCase(s.repository()))
        throw new InvalidContractException("Repository binding changed");
      var candidate =
          github.fetch(
              repo, job.pullNumber(), s.contractPath()); // No DB transaction during network I/O.
      tx.executeWithoutResult(
          status -> {
            if (!jobs.lockOwned(job)) return;
            if (!job.headSha().equalsIgnoreCase(candidate.source().headSha())) {
              jobs.finish(job, "SUPERSEDED", null, "HEAD_CHANGED");
              return;
            }
            Map<String, Object> before =
                json.readValue(
                    baseline.documentJson(), new TypeReference<Map<String, Object>>() {});
            Map<String, List<String>> dependencies =
                json.readValue(
                    s.dependenciesJson(), new TypeReference<Map<String, List<String>>>() {});
            var saved =
                history.createForTenant(
                    s.tenantId(),
                    new OpenApiRequest(
                        baseline.serviceName(), before, candidate.document(), dependencies),
                    baseline.id(),
                    candidate.source());
            jobs.finish(
                job,
                "SUCCEEDED",
                saved.id(),
                null); // Evidence and terminal state commit atomically.
          });
    } catch (GitHubIntegrationException e) {
      jobs.fail(
          job, "GITHUB_UNAVAILABLE", e.status().is5xxServerError() || e.status().value() == 429);
    } catch (InvalidContractException e) {
      jobs.fail(job, "INVALID_CONTRACT_OR_BINDING", false);
    } catch (ResourceNotFoundException e) {
      jobs.fail(job, "GITHUB_NOT_FOUND", false);
    } catch (RuntimeException e) {
      LOG.error(
          "Webhook analysis failed for delivery {} ({})",
          job.deliveryId(),
          e.getClass().getSimpleName());
      jobs.fail(job, "ANALYSIS_FAILED", true);
    }
  }
}
