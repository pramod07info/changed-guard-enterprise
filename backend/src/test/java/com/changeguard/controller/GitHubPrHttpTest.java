package com.changeguard.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.changeguard.dto.*;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.integration.github.GitHubPrClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "changeguard.github.enabled=true")
@Import(GitHubPrHttpTest.Fixtures.class)
class GitHubPrHttpTest {
    static final String TENANT = "github-pr-" + UUID.randomUUID();
    @TestConfiguration static class Fixtures {
        @Bean @Primary JwtDecoder githubDecoder() {
            return token -> {
                if (!token.equals("pr-tenant") && !token.equals("pr-other")) throw new BadJwtException("Invalid fixture");
                return Jwt.withTokenValue(token).header("alg", "fixture").subject("publisher")
                        .claim("tenant_id", token.equals("pr-tenant") ? TENANT : "other-pr-tenant").build();
            };
        }
    }
    @MockitoBean GitHubPrClient github;
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<String, Object> document = Map.of("openapi", "3.0.3", "paths", Map.of());
    private final GitHubSource source = new GitHubSource("team/payments", "team/payments", 7,
            "https://github.com/team/payments/pull/7", "a".repeat(40), "b".repeat(40), "openapi.json", "c".repeat(40), Instant.parse("2026-10-12T05:00:00Z"));
    private HttpResponse<String> request(String token, String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private BaselineResponse baseline() throws Exception {
        var project = json.readValue(request("pr-tenant", "POST", "/projects", new ProjectRequest("Payments")).body(), ProjectResponse.class);
        var service = json.readValue(request("pr-tenant", "POST", "/projects/" + project.id() + "/services",
                new CatalogServiceRequest("payments", "team", "https://github.com/team/payments")).body(), CatalogServiceResponse.class);
        var result = request("pr-tenant", "POST", "/services/" + service.id() + "/baselines", new BaselineRequest("1.0", "production", document));
        assertEquals(201, result.statusCode(), result.body());
        return json.readValue(result.body(), BaselineResponse.class);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM analysis_report WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM contract_snapshot WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM contract_baseline WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM catalog_service WHERE tenant_id = ?", TENANT);
        jdbc.update("DELETE FROM catalog_project WHERE tenant_id = ?", TENANT);
    }
    @Test void previewAndSavedHistoryPreservePinnedSource() throws Exception {
        var baseline = baseline();
        when(github.fetch(any(), eq(7), eq("openapi.json"))).thenReturn(new GitHubPrClient.Candidate(source, document));
        var input = new GitHubPrRequest(7, "openapi.json", Map.of());
        String prefix = "/baselines/" + baseline.id() + "/github-pr";
        var preview = request("pr-tenant", "POST", prefix + "/preview", input);
        assertEquals(200, preview.statusCode(), preview.body());
        assertEquals(source, json.readValue(preview.body(), GitHubPrPreview.class).source());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_report WHERE tenant_id = ?", Integer.class, TENANT));
        var result = request("pr-tenant", "POST", prefix + "/analyses", input);
        assertEquals(201, result.statusCode(), result.body());
        var saved = json.readValue(result.body(), SavedAnalysis.class);
        assertEquals(source, saved.githubSource()); assertEquals(baseline.id(), saved.baselineId());
        var loaded = json.readValue(request("pr-tenant", "GET", "/analyses/" + saved.id(), null).body(), SavedAnalysis.class);
        assertEquals(source, loaded.githubSource());
        assertEquals(404, request("pr-other", "GET", "/analyses/" + saved.id(), null).statusCode());
    }
    @Test void otherTenantAndAnonymousRequestsCannotFetchGitHub() throws Exception {
        var baseline = baseline(); var input = new GitHubPrRequest(7, "openapi.json", Map.of());
        for (String suffix : new String[]{"/preview", "/analyses"}) {
            String path = "/baselines/" + baseline.id() + "/github-pr" + suffix;
            assertEquals(404, request("pr-other", "POST", path, input).statusCode());
            assertEquals(401, request(null, "POST", path, input).statusCode());
        }
        verifyNoInteractions(github);
    }
    @Test void invalidInputsCannotFetchGitHub() throws Exception {
        var baseline = baseline(); String path = "/baselines/" + baseline.id() + "/github-pr/analyses";
        assertEquals(400, request("pr-tenant", "POST", path, new GitHubPrRequest(0, "openapi.json", Map.of())).statusCode());
        assertEquals(400, request("pr-tenant", "POST", path, new GitHubPrRequest(7, "../openapi.json", Map.of())).statusCode());
        verifyNoInteractions(github);
    }
    @Test void upstreamFailureAndUnsupportedContractDoNotWriteEvidence() throws Exception {
        var baseline = baseline(); String path = "/baselines/" + baseline.id() + "/github-pr/analyses";
        var input = new GitHubPrRequest(7, "openapi.json", Map.of());
        when(github.fetch(any(), eq(7), eq("openapi.json"))).thenThrow(new GitHubIntegrationException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub rate limit; retry later"));
        assertEquals(503, request("pr-tenant", "POST", path, input).statusCode());
        when(github.fetch(any(), eq(7), eq("openapi.json"))).thenReturn(new GitHubPrClient.Candidate(source, Map.of("openapi", "2.0", "paths", Map.of())));
        assertEquals(400, request("pr-tenant", "POST", path, input).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_report WHERE tenant_id = ?", Integer.class, TENANT));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM contract_snapshot WHERE tenant_id = ?", Integer.class, TENANT));
    }
}
