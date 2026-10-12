package com.changeguard.dto;

import java.time.Instant;
import java.util.UUID;

public record SavedAnalysis(UUID id, String service, Instant createdAt,
        UUID beforeSnapshotId, UUID afterSnapshotId, OpenApiReport report, UUID baselineId, GitHubSource githubSource) {
    public SavedAnalysis(UUID id, String service, Instant createdAt,
            UUID beforeSnapshotId, UUID afterSnapshotId, OpenApiReport report) {
        this(id, service, createdAt, beforeSnapshotId, afterSnapshotId, report, null, null);
    }
    public SavedAnalysis(UUID id, String service, Instant createdAt,
            UUID beforeSnapshotId, UUID afterSnapshotId, OpenApiReport report, UUID baselineId) {
        this(id, service, createdAt, beforeSnapshotId, afterSnapshotId, report, baselineId, null);
    }
}
