package com.changeguard.dto;

import java.time.Instant;
import java.util.UUID;

public record CatalogServiceResponse(UUID id, UUID projectId, String name, String owner,
        String repositoryUrl, Instant createdAt) {}
