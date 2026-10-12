package com.changeguard.dto;

import java.util.UUID;

public record GitHubWebhookResponse(
    UUID id, UUID baselineId, String repository, String contractPath, String deliveryPath) {}
