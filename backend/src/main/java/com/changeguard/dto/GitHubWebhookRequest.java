package com.changeguard.dto;

import java.util.List;
import java.util.Map;

public record GitHubWebhookRequest(String contractPath, Map<String, List<String>> dependencies) {}
