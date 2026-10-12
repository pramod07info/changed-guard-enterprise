package com.changeguard.dto;

import java.util.List;
import java.util.Map;

public record GitHubPrRequest(Integer pullNumber, String contractPath, Map<String, List<String>> dependencies) {}
