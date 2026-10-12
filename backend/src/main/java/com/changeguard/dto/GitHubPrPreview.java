package com.changeguard.dto;

import java.util.UUID;

public record GitHubPrPreview(UUID baselineId, GitHubSource source, OpenApiReport report) {}
