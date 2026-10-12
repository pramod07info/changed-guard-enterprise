package com.changeguard.service;

import com.changeguard.dto.BaselineAnalysisRequest;
import com.changeguard.dto.BaselineResponse;
import com.changeguard.dto.GitHubPrPreview;
import com.changeguard.dto.GitHubPrRequest;
import com.changeguard.dto.SavedAnalysis;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.integration.github.GitHubPrClient;
import com.changeguard.integration.github.GitHubRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GitHubPrService {
  private final BaselineService baselines;
  private final CatalogService catalog;
  private final GitHubPrClient github;
  private final DependencyGraphService graph;
  private final boolean enabled;

  @org.springframework.beans.factory.annotation.Autowired
  public GitHubPrService(
      BaselineService baselines,
      CatalogService catalog,
      GitHubPrClient github,
      DependencyGraphService graph,
      com.changeguard.config.FeatureFlags flags) {
    this.baselines = baselines;
    this.catalog = catalog;
    this.github = github;
    this.graph = graph;
    this.enabled = flags.manifest().githubPr();
  }

  public GitHubPrService(
      BaselineService baselines,
      CatalogService catalog,
      GitHubPrClient github,
      DependencyGraphService graph,
      boolean enabled) {
    this.baselines = baselines;
    this.catalog = catalog;
    this.github = github;
    this.graph = graph;
    this.enabled = enabled;
  }

  public GitHubPrPreview preview(UUID id, GitHubPrRequest request) {
    var baseline = baselines.get(id); // Tenant check happens before any outbound request.
    var candidate = candidate(baseline, request);
    var report =
        baselines.preview(
            id, new BaselineAnalysisRequest(candidate.document(), request.dependencies()));
    return new GitHubPrPreview(id, candidate.source(), report);
  }

  public SavedAnalysis analyzeAndSave(UUID id, GitHubPrRequest request) {
    var baseline = baselines.get(id);
    var candidate = candidate(baseline, request);
    return baselines.analyzeGitHubAndSave(
        id,
        new BaselineAnalysisRequest(candidate.document(), request.dependencies()),
        candidate.source());
  }

  private GitHubPrClient.Candidate candidate(BaselineResponse baseline, GitHubPrRequest request) {
    if (request == null || request.pullNumber() == null || request.pullNumber() < 1)
      throw new InvalidContractException("pullNumber must be positive");
    GitHubPrClient.validatePath(request.contractPath());
    graph.validate(baseline.serviceName(), request.dependencies());
    var repository =
        GitHubRepository.fromCatalogUrl(catalog.service(baseline.serviceId()).repositoryUrl());
    if (!enabled)
      throw new GitHubIntegrationException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "GitHub PR integration is disabled; set CHANGEGUARD_GITHUB_ENABLED=true on the backend");
    return github.fetch(repository, request.pullNumber(), request.contractPath());
  }
}
