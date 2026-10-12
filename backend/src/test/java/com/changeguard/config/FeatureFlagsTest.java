package com.changeguard.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class FeatureFlagsTest {
  @Test
  void defaultsPreserveCoreFeaturesAndGithubRemainsOptIn() {
    var f = new FeatureFlags(new MockEnvironment()).manifest();
    assertTrue(f.openApiAnalysis());
    assertTrue(f.databaseAnalysis());
    assertTrue(f.baselines());
    assertTrue(f.analysisHistory());
    assertTrue(f.dependencyAnalysis());
    assertTrue(f.testRecommendations());
    assertFalse(f.githubPr());
    assertFalse(f.webhooks());
    assertFalse(f.githubChecks());
  }

  @Test
  void upstreamDisabledFlagsCascade() {
    for (String key : new String[] {"catalog", "baselines", "open-api-analysis"}) {
      var f =
          new FeatureFlags(
                  github().withProperty("changeguard.features." + key + ".enabled", "false"))
              .manifest();
      assertFalse(f.baselines());
      assertFalse(f.githubPr());
      assertFalse(f.webhooks());
      assertFalse(f.githubChecks());
    }
  }

  @Test
  void historyDisabledAllowsPreviewsButPreventsAutomation() {
    var f =
        new FeatureFlags(
                github().withProperty("changeguard.features.analysis-history.enabled", "false"))
            .manifest();
    assertTrue(f.openApiAnalysis());
    assertTrue(f.githubPr());
    assertFalse(f.analysisHistory());
    assertFalse(f.webhooks());
    assertFalse(f.githubChecks());
  }

  @Test
  void databaseAndChecksHaveIndependentSwitches() {
    var f =
        new FeatureFlags(
                github()
                    .withProperty("changeguard.features.database-analysis.enabled", "false")
                    .withProperty("changeguard.features.github-checks.enabled", "false"))
            .manifest();
    assertFalse(f.databaseAnalysis());
    assertFalse(f.databaseHistory());
    assertTrue(f.webhooks());
    assertFalse(f.githubChecks());
    assertTrue(f.analysisHistory());
  }

  static MockEnvironment github() {
    return new MockEnvironment()
        .withProperty("changeguard.github.enabled", "true")
        .withProperty("changeguard.github.webhooks.enabled", "true")
        .withProperty("changeguard.github.app.enabled", "true");
  }
}
