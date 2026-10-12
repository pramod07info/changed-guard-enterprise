package com.changeguard.entity;

import java.time.Instant;
import java.util.UUID;

public record ContractBaseline(UUID id, String tenantId, UUID serviceId, String serviceName,
        String version, String environment, String documentJson, String publishedBy, Instant publishedAt) {}
