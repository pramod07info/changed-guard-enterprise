package com.changeguard.entity;

import java.time.Instant;
import java.util.UUID;

public record CatalogProject(UUID id, String tenantId, String name, String createdBy, Instant createdAt) {}
