package com.changeguard.dto;

import java.util.List;

public record ImpactReport(String service, Risk risk, List<Finding> breakingChanges,
        List<Consumer> potentialConsumers, List<String> recommendedTests,
        boolean requiresReview, List<String> limitations) {
    public enum Risk { NONE, LOW, HIGH }
    public record Finding(String code, String endpoint, String field, Risk severity) {}
    public record Consumer(String service, int distance, List<String> path) {}
}
