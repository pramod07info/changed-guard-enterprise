package com.changeguard.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, String name, String createdBy, Instant createdAt) {}
