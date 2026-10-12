package com.changeguard.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.changeguard.config.FeatureFlags;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class FeatureGateFilterTest {
  @Test
  void everyApiModuleIsGatedAndCoreSessionPathsRemainAvailable() throws Exception {
    var env = new MockEnvironment();
    for (String key :
        new String[] {
          "normalized-analysis",
          "open-api-analysis",
          "analysis-history",
          "catalog",
          "baselines",
          "database-analysis",
          "database-history",
          "metrics"
        }) env.withProperty("changeguard.features." + key + ".enabled", "false");
    var gate = new FeatureGateFilter(new FeatureFlags(env), JsonMapper.builder().build());
    for (String path :
        new String[] {
          "/api/v1/impact",
          "/api/v1/impact/openapi",
          "/api/v1/impact/database",
          "/api/v1/analyses",
          "/api/v1/analyses/id",
          "/api/v1/snapshots/id",
          "/api/v1/database-analyses",
          "/api/v1/projects",
          "/api/v1/projects/id/services",
          "/api/v1/services/id/baselines",
          "/api/v1/baselines/id/github-pr/preview",
          "/api/v1/github/webhooks/id/deliveries",
          "/actuator/prometheus",
          "/actuator/info"
        }) {
      var request = new MockHttpServletRequest();
      request.setServletPath(path + "/");
      var response = new MockHttpServletResponse();
      var chain = mock(FilterChain.class);
      gate.doFilter(request, response, chain);
      assertEquals(503, response.getStatus(), path);
      verifyNoInteractions(chain);
      assertTrue(response.getContentAsString().contains("Feature disabled"));
    }
    for (String path :
        new String[] {
          "/api/v1/features",
          "/api/v1/session",
          "/api/v1/auth/config",
          "/actuator/health",
          "/oauth2/token"
        }) assertNull(gate.disabledFeature(path));
  }

  @Test
  void historyOffAllowsBaselineAndGithubPreviewsButNotTheirSaveRoutes() {
    var env =
        new MockEnvironment()
            .withProperty("changeguard.github.enabled", "true")
            .withProperty("changeguard.features.analysis-history.enabled", "false");
    var gate = new FeatureGateFilter(new FeatureFlags(env), JsonMapper.builder().build());
    assertNull(gate.disabledFeature("/api/v1/baselines/id/preview"));
    assertNull(gate.disabledFeature("/api/v1/baselines/id/github-pr/preview"));
    assertEquals("analysisHistory", gate.disabledFeature("/api/v1/baselines/id/analyses"));
    assertEquals(
        "analysisHistory", gate.disabledFeature("/api/v1/baselines/id/github-pr/analyses"));
  }

  @Test
  void checksOffLeavesDeliveryRoutesAvailable() {
    var env =
        new MockEnvironment()
            .withProperty("changeguard.github.enabled", "true")
            .withProperty("changeguard.github.webhooks.enabled", "true")
            .withProperty("changeguard.github.app.enabled", "true")
            .withProperty("changeguard.features.github-checks.enabled", "false");
    var gate = new FeatureGateFilter(new FeatureFlags(env), JsonMapper.builder().build());
    assertNull(gate.disabledFeature("/api/v1/github/webhooks/id/deliveries"));
    assertEquals(
        "githubChecks",
        gate.disabledFeature("/api/v1/github/webhooks/id/deliveries/id/check/retry"));
  }
}
