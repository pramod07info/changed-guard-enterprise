package com.changeguard.entity;

public record ApplicationUser(String username, String passwordHash, String tenantId, boolean enabled) {}
