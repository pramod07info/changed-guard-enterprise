package com.changeguard.impact;

import java.util.List;
import java.util.Map;

public record OpenApiRequest(String service, Map<String, Object> before,
        Map<String, Object> after, Map<String, List<String>> dependencies) {}
