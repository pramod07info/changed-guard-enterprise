package com.changeguard.impact;

import java.util.List;
import java.util.Map;

/** Normalized response fields; dependency direction is consumer -> providers. */
public record ImpactRequest(String service,
        Map<String, Map<String, String>> before,
        Map<String, Map<String, String>> after,
        Map<String, List<String>> dependencies) {}
