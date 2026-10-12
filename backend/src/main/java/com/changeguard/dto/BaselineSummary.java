package com.changeguard.dto;

import java.time.Instant;
import java.util.UUID;

public record BaselineSummary(UUID id, UUID serviceId, String serviceName, String version,
        String environment, String publishedBy, Instant publishedAt) {}
