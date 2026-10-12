package com.changeguard.dto;

public record GitHubCheckStatus(
    String state, int attempts, Long checkRunId, String checkName, String errorCode) {}
