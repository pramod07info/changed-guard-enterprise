package com.changeguard.integration.github;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;

public final class GitHubHttpTransport implements GitHubTransport {
    private final HttpClient client;
    private final URI origin;
    public GitHubHttpTransport(HttpClient client, URI origin) { this.client = client; this.origin = origin; }
    @Override public byte[] get(String path) {
        if (!path.startsWith("/repos/") || !origin.resolve(path).getAuthority().equals(origin.getAuthority()))
            throw new IllegalArgumentException("GitHub transport accepts repository paths only");
        var request = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "ChangeGuard").GET().build();
        var pending = client.sendAsync(request, info -> new BoundedBodySubscriber(2 * 1024 * 1024));
        try {
            var response = pending.get(20, TimeUnit.SECONDS);
            int status = response.statusCode();
            if (status == 404) throw new ResourceNotFoundException("Public GitHub pull request or contract file not found");
            if (status == 403 || status == 429)
                throw new GitHubIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub rate limit or access restriction; retry later");
            if (status != 200) throw new GitHubIntegrationException(HttpStatus.BAD_GATEWAY, "GitHub could not provide the requested public repository data");
            return response.body();
        } catch (TimeoutException exception) {
            pending.cancel(true);
            throw new GitHubIntegrationException(HttpStatus.GATEWAY_TIMEOUT, "GitHub request timed out");
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw new GitHubIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub request interrupted");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof GitHubIntegrationException bounded) throw bounded;
            if (exception.getCause() instanceof HttpTimeoutException)
                throw new GitHubIntegrationException(HttpStatus.GATEWAY_TIMEOUT, "GitHub request timed out");
            throw new GitHubIntegrationException(HttpStatus.BAD_GATEWAY, "Unable to read GitHub repository data");
        }
    }
}
