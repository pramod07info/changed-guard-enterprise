package com.changeguard.entity;

import java.util.UUID;

public record GitHubWebhookSubscription(
    UUID id,
    String tenantId,
    UUID baselineId,
    String repository,
    String contractPath,
    String dependenciesJson,
    String createdBy) {}
