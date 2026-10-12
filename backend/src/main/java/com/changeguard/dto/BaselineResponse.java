package com.changeguard.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BaselineResponse(UUID id, UUID serviceId, String serviceName, String version,
        String environment, Map<String, Object> document, String publishedBy, Instant publishedAt) {}
