package com.changeguard.impact;

import java.util.List;

public record OpenApiReport(String service, ImpactReport.Risk risk,
        List<Change> breakingChanges, List<ImpactReport.Consumer> potentialConsumers,
        List<String> recommendedTests, boolean requiresReview, List<String> limitations) {
    public record Change(String code, String operation, String location, String explanation) {}
}
