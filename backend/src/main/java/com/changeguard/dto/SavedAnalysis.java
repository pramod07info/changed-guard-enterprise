package com.changeguard.dto;

import java.time.Instant;
import java.util.UUID;

public record SavedAnalysis(UUID id, String service, Instant createdAt,
        UUID beforeSnapshotId, UUID afterSnapshotId, OpenApiReport report) {}
