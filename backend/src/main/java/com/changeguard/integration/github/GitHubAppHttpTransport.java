package com.changeguard.integration.github;

import com.changeguard.exception.GitHubIntegrationException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

public final class GitHubAppHttpTransport implements GitHubAppTransport {
  private final HttpClient client;
  private final URI origin;
  private final JsonMapper json;

  public GitHubAppHttpTransport(HttpClient client, URI origin, JsonMapper json) {
    if (client.followRedirects() != HttpClient.Redirect.NEVER)
      throw new IllegalArgumentException("App transport must disable redirects");
    this.client = client;
    this.origin = origin;
    this.json = json;
  }

  @Override
  public byte[] request(String method, String path, String bearer, Map<String, Object> body) {
    URI target = origin.resolve(path);
    if (!Set.of("GET", "POST", "PATCH").contains(method)
        || !path.startsWith("/")
        || path.startsWith("//")
        || !target.normalize().equals(target)
        || !target.getAuthority().equals(origin.getAuthority())
        || !target.getScheme().equals(origin.getScheme())
        || target.getFragment() != null
        || !(path.startsWith("/repos/")
            || path.matches("/app/installations/[1-9][0-9]*/access_tokens")))
      throw new IllegalArgumentException("Invalid GitHub App API path");
    var builder =
        HttpRequest.newBuilder(target)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ChangeGuard");
    if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
    byte[] bytes = body == null ? new byte[0] : json.writeValueAsBytes(body);
    if (bytes.length > 65536) throw new IllegalArgumentException("Check request exceeds 64 KiB");
    if (body != null) builder.header("Content-Type", "application/json");
    var pending =
        client.sendAsync(
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
            info -> new BoundedBodySubscriber(2 * 1024 * 1024));
    try {
      var response = pending.get(20, TimeUnit.SECONDS);
      int status = response.statusCode();
      if (status >= 200 && status < 300) return response.body();
      // Do not persist or log credential-bearing upstream bodies.
      boolean limited =
          status == 403
              && (response.headers().firstValue("X-RateLimit-Remaining").orElse("").equals("0")
                  || response.headers().firstValue("Retry-After").isPresent());
      HttpStatus mapped =
          limited
              ? HttpStatus.SERVICE_UNAVAILABLE
              : (status == 401 || status == 403 || status == 404 || status == 422)
                  ? HttpStatus.UNPROCESSABLE_ENTITY
                  : (status == 429 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY);
      throw new GitHubIntegrationException(mapped, "GitHub App request failed");
    } catch (TimeoutException e) {
      pending.cancel(true);
      throw unavailable();
    } catch (InterruptedException e) {
      pending.cancel(true);
      Thread.currentThread().interrupt();
      throw unavailable();
    } catch (ExecutionException e) {
      pending.cancel(true);
      throw unavailable();
    }
  }

  private GitHubIntegrationException unavailable() {
    return new GitHubIntegrationException(
        HttpStatus.SERVICE_UNAVAILABLE, "GitHub App request unavailable");
  }
}
