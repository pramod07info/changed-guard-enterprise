package com.changeguard.exception;

import org.springframework.http.HttpStatus;

public class GitHubIntegrationException extends RuntimeException {
    private final HttpStatus status;
    public GitHubIntegrationException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
}
