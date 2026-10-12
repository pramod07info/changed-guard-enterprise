package com.changeguard.dto;

import java.util.List;

public record DatabaseSchemaReport(
    String service,
    ImpactReport.Risk risk,
    List<Change> changes,
    List<ImpactReport.Consumer> potentialConsumers,
    List<String> recommendedTests,
    boolean requiresReview,
    List<String> limitations) {
  public record Change(
      String code, String table, String location, String explanation, ImpactReport.Risk severity) {}
}
