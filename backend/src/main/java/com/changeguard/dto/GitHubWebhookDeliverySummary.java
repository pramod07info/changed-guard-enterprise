package com.changeguard.dto;

import java.time.Instant;
import java.util.UUID;

public record GitHubWebhookDeliverySummary(
    UUID deliveryId,
    String state,
    int attempts,
    UUID analysisId,
    String errorCode,
    int pullNumber,
    String headSha,
    Instant createdAt,
    GitHubCheckStatus check) {}
