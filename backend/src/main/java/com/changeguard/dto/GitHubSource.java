package com.changeguard.dto;

import java.time.Instant;

public record GitHubSource(String repository, String headRepository, int pullNumber, String pullUrl,
        String baseSha, String headSha, String contractPath, String blobSha, Instant fetchedAt) {}
