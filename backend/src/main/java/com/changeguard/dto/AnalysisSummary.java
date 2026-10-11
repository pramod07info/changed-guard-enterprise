package com.changeguard.dto;

import java.time.Instant;
import java.util.UUID;

public record AnalysisSummary(UUID id, String service, Instant createdAt, ImpactReport.Risk risk,
        boolean requiresReview) {}
