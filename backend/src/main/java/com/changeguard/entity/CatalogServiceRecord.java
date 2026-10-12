package com.changeguard.entity;

import java.time.Instant;
import java.util.UUID;

public record CatalogServiceRecord(UUID id, String tenantId, UUID projectId, String name,
        String owner, String repositoryUrl, Instant createdAt) {}
