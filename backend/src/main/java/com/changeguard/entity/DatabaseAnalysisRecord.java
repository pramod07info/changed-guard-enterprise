package com.changeguard.entity;

import java.time.Instant;
import java.util.UUID;

public record DatabaseAnalysisRecord(
    UUID id,
    String tenantId,
    String service,
    Instant createdAt,
    String beforeJson,
    String afterJson,
    String dependenciesJson,
    String reportJson) {}
