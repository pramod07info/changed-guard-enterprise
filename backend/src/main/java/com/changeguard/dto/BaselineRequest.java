package com.changeguard.dto;

import java.util.Map;

public record BaselineRequest(String version, String environment, Map<String, Object> document) {}
