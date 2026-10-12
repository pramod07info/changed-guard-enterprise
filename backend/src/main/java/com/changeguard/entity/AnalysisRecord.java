package com.changeguard.entity;

import java.time.Instant;
import java.util.UUID;

public record AnalysisRecord(UUID id, String tenantId, String service, Instant createdAt,
        UUID beforeSnapshotId, UUID afterSnapshotId, String reportJson, String dependenciesJson, UUID baselineId) {
    public AnalysisRecord(UUID id, String tenantId, String service, Instant createdAt,
            UUID beforeSnapshotId, UUID afterSnapshotId, String reportJson, String dependenciesJson) {
        this(id, tenantId, service, createdAt, beforeSnapshotId, afterSnapshotId, reportJson, dependenciesJson, null);
    }
}
