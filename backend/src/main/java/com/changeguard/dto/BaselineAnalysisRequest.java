package com.changeguard.dto;

import java.util.List;
import java.util.Map;

public record BaselineAnalysisRequest(Map<String, Object> after, Map<String, List<String>> dependencies) {}
