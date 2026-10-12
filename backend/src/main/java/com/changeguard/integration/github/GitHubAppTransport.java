package com.changeguard.integration.github;

import java.util.Map;

public interface GitHubAppTransport {
  byte[] request(String method, String path, String bearer, Map<String, Object> body);
}
