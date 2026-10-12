package com.changeguard.service;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.config.FeatureFlags;
import com.changeguard.dto.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

class FeatureAnalysisTest {
  @Test
  void disablingEnrichmentsPreservesCompatibilityRisk() {
    var flags =
        new FeatureFlags(
            new MockEnvironment()
                .withProperty("changeguard.features.dependency-analysis.enabled", "false")
                .withProperty("changeguard.features.test-recommendations.enabled", "false"));
    var graph = new DependencyGraphService(flags);
    var deps = Map.of("orders", List.of("payments"));
    var impact =
        new ImpactService(graph, flags)
            .analyze(
                new ImpactRequest(
                    "payments", Map.of("GET /payments", Map.of("id", "string")), Map.of(), deps));
    assertEquals(ImpactReport.Risk.HIGH, impact.risk());
    assertTrue(impact.requiresReview());
    assertTrue(impact.potentialConsumers().isEmpty());
    assertTrue(impact.recommendedTests().isEmpty());
    var schema =
        new DatabaseSchemaService(graph, JsonMapper.builder().build(), flags)
            .analyze(
                new DatabaseSchemaRequest(
                    "payments",
                    Map.of(
                        "dialect",
                        "postgresql",
                        "tables",
                        Map.of(
                            "public.payments",
                            Map.of(
                                "columns",
                                Map.of("id", Map.of("type", "uuid", "nullable", false))))),
                    Map.of("dialect", "postgresql", "tables", Map.of()),
                    deps));
    assertEquals(ImpactReport.Risk.HIGH, schema.risk());
    assertFalse(schema.changes().isEmpty());
    assertTrue(schema.potentialConsumers().isEmpty());
    assertTrue(schema.recommendedTests().isEmpty());
  }

  @Test
  void recommendationsCanBeDisabledIndependentlyOfGraph() {
    var flags =
        new FeatureFlags(
            new MockEnvironment()
                .withProperty("changeguard.features.test-recommendations.enabled", "false"));
    var report =
        new ImpactService(new DependencyGraphService(flags), flags)
            .analyze(
                new ImpactRequest(
                    "payments",
                    Map.of("GET /p", Map.of("id", "string")),
                    Map.of(),
                    Map.of("orders", List.of("payments"))));
    assertEquals(1, report.potentialConsumers().size());
    assertTrue(report.recommendedTests().isEmpty());
  }
}
