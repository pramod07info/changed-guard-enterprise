package com.changeguard.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SavedDatabaseAnalysis(
    UUID id,
    String service,
    Instant createdAt,
    DatabaseSchemaReport report,
    Map<String, Object> before,
    Map<String, Object> after,
    Map<String, List<String>> dependencies) {}
