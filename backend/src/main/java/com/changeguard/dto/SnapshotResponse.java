package com.changeguard.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SnapshotResponse(UUID id, String service, Instant createdAt, Map<String, Object> document) {}
