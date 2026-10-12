package com.changeguard.dto;

import java.util.UUID;

public record GitHubWebhookDelivery(
    UUID deliveryId, String state, int attempts, UUID analysisId, String errorCode) {}
