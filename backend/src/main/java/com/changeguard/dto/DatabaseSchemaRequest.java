package com.changeguard.dto;

import java.util.List;
import java.util.Map;

public record DatabaseSchemaRequest(
    String service,
    Map<String, Object> before,
    Map<String, Object> after,
    Map<String, List<String>> dependencies) {}
