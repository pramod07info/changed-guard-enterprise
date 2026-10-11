package com.changeguard.entity;

import java.time.Instant;
import java.util.UUID;

/** JDBC persistence model; JSON is serialized before reaching the repository. */
public record ContractSnapshot(UUID id, String tenantId, String service, Instant createdAt, String documentJson) {}
