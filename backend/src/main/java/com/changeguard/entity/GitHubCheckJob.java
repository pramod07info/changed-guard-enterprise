package com.changeguard.entity;

import java.util.UUID;

public record GitHubCheckJob(
    String tenantId,
    UUID subscriptionId,
    UUID deliveryId,
    long installationId,
    String repository,
    String headSha,
    String deliveryState,
    UUID analysisId,
    String reportJson,
    int attempts,
    UUID leaseToken) {
  public String externalId() {
    return "changeguard:" + subscriptionId + ":" + deliveryId;
  }
}
