package com.changeguard.integration.github;

public interface GitHubTransport {
    byte[] get(String path);
}
