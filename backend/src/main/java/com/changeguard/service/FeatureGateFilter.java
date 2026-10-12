package com.changeguard.service;

import com.changeguard.config.FeatureFlags;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Deployment capability gate after Spring Security and before controller side effects. */
public class FeatureGateFilter extends OncePerRequestFilter {
  private final FeatureFlags flags;
  private final JsonMapper json;

  public FeatureGateFilter(FeatureFlags flags, JsonMapper json) {
    this.flags = flags;
    this.json = json;
  }

  public String disabledFeature(String path) {
    var f = flags.manifest();
    if (path.equals("/api/v1/impact") && !f.normalizedAnalysis()) return "normalizedAnalysis";
    if (under(path, "/api/v1/impact/openapi") && !f.openApiAnalysis()) return "openApiAnalysis";
    if ((under(path, "/api/v1/analyses") || under(path, "/api/v1/snapshots"))
        && !f.analysisHistory()) return "analysisHistory";
    if (under(path, "/api/v1/impact/database") && !f.databaseAnalysis()) return "databaseAnalysis";
    if (under(path, "/api/v1/database-analyses") && !f.databaseHistory()) return "databaseHistory";
    if ((under(path, "/api/v1/projects") || under(path, "/api/v1/services")) && !f.catalog())
      return "catalog";
    if ((under(path, "/api/v1/baselines")
            || path.matches("/api/v1/services/[^/]+/baselines(?:/.*)?"))
        && !f.baselines()) return "baselines";
    if (under(path, "/api/v1/baselines")) {
      if (path.contains("/github-pr/") && !f.githubPr()) return "githubPr";
      if (path.endsWith("/analyses") && !f.analysisHistory()) return "analysisHistory";
      if (path.contains("/github-webhooks") && !f.webhooks()) return "webhooks";
    }
    if (under(path, "/api/v1/github/webhooks")) {
      if (!f.webhooks()) return "webhooks";
      if (path.matches(".*/check(?:/retry)?/?") && !f.githubChecks()) return "githubChecks";
    }
    if ((under(path, "/actuator/prometheus") || under(path, "/actuator/info")) && !f.metrics())
      return "metrics";
    return null;
  }

  private boolean under(String path, String prefix) {
    return path.equals(prefix) || path.startsWith(prefix + "/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getServletPath();
    if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
    String feature = disabledFeature(path);
    if (feature == null) {
      chain.doFilter(request, response);
      return;
    }
    var problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE, "This feature is disabled by the server operator.");
    problem.setTitle("Feature disabled");
    problem.setProperty("feature", feature);
    response.setStatus(503);
    response.setContentType("application/problem+json");
    response.getWriter().write(json.writeValueAsString(problem));
  }
}
