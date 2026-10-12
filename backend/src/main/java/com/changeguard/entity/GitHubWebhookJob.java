package com.changeguard.entity;

import java.util.UUID;

public record GitHubWebhookJob(
    GitHubWebhookSubscription subscription,
    UUID deliveryId,
    String headSha,
    int pullNumber,
    int attempts,
    UUID leaseToken) {}
