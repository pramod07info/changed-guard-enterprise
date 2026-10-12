package com.changeguard.config;

import com.changeguard.dto.FeatureManifest;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Immutable deployment flags. Dependencies are resolved once at startup. */
@Component
public class FeatureFlags {
  private final FeatureManifest manifest;

  public FeatureFlags(Environment env) {
    boolean normalizedAnalysis = enabled(env, "normalized-analysis");
    boolean openApiAnalysis = enabled(env, "open-api-analysis");
    boolean analysisHistory = enabled(env, "analysis-history");
    boolean catalog = enabled(env, "catalog");
    boolean baselines = enabled(env, "baselines");
    boolean databaseAnalysis = enabled(env, "database-analysis");
    boolean databaseHistory = enabled(env, "database-history");
    boolean dependencyAnalysis = enabled(env, "dependency-analysis");
    boolean testRecommendations = enabled(env, "test-recommendations");
    boolean githubPr = enabled(env, "github-pr");
    boolean webhooks = enabled(env, "webhooks");
    boolean githubChecks = enabled(env, "github-checks");
    boolean metrics = enabled(env, "metrics");
    analysisHistory &= openApiAnalysis;
    baselines &= catalog && openApiAnalysis;
    databaseHistory &= databaseAnalysis;
    githubPr &= baselines && env.getProperty("changeguard.github.enabled", Boolean.class, false);
    webhooks &=
        githubPr
            && analysisHistory
            && env.getProperty("changeguard.github.webhooks.enabled", Boolean.class, false);
    githubChecks &=
        webhooks && env.getProperty("changeguard.github.app.enabled", Boolean.class, false);
    manifest =
        new FeatureManifest(
            normalizedAnalysis,
            openApiAnalysis,
            analysisHistory,
            catalog,
            baselines,
            databaseAnalysis,
            databaseHistory,
            dependencyAnalysis,
            testRecommendations,
            githubPr,
            webhooks,
            githubChecks,
            metrics);
  }

  private boolean enabled(Environment env, String name) {
    return env.getProperty("changeguard.features." + name + ".enabled", Boolean.class, true);
  }

  public FeatureManifest manifest() {
    return manifest;
  }
}
