package com.changeguard.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.json.JsonMapper;
import com.changeguard.dto.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CatalogHttpTest.Fixtures.class)
class CatalogHttpTest {
    static final String TENANT_A = "catalog-a-" + UUID.randomUUID();
    static final String TENANT_B = "catalog-b-" + UUID.randomUUID();
    @TestConfiguration static class Fixtures {
        @Bean @Primary JwtDecoder catalogDecoder() {
            return token -> {
                if (!token.equals("catalog-a") && !token.equals("catalog-b")) throw new BadJwtException("Invalid fixture");
                return Jwt.withTokenValue(token).header("alg", "fixture").subject("catalog-publisher")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                        .claim("tenant_id", token.equals("catalog-a") ? TENANT_A : TENANT_B).build();
            };
        }
    }
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<String, Object> document = Map.of("openapi", "3.0.3", "paths", Map.of());
    private HttpResponse<String> request(String token, String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.header("Content-Type", "application/json").method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private ProjectResponse project(String token, String name) throws Exception {
        var response = request(token, "POST", "/projects", new ProjectRequest(name));
        assertEquals(201, response.statusCode(), response.body());
        var result = json.readValue(response.body(), ProjectResponse.class);
        assertEquals("/api/v1/projects/" + result.id(), response.headers().firstValue("Location").orElseThrow());
        return result;
    }
    private CatalogServiceResponse service(String token, UUID project, String name) throws Exception {
        var response = request(token, "POST", "/projects/" + project + "/services", new CatalogServiceRequest(name, "platform-team", "https://github.com/team/payments"));
        assertEquals(201, response.statusCode(), response.body());
        return json.readValue(response.body(), CatalogServiceResponse.class);
    }
    private BaselineResponse baseline(String token, UUID service) throws Exception {
        var response = request(token, "POST", "/services/" + service + "/baselines", new BaselineRequest("1.0.0", "production", document));
        assertEquals(201, response.statusCode(), response.body());
        return json.readValue(response.body(), BaselineResponse.class);
    }
    @AfterEach void cleanup() {
        for (String tenant : new String[]{TENANT_A, TENANT_B}) {
            jdbc.update("DELETE FROM analysis_report WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM contract_snapshot WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM contract_baseline WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM catalog_service WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM catalog_project WHERE tenant_id = ?", tenant);
        }
    }
    @Test void createPublishCompareSaveAndReadEvidence() throws Exception {
        var project = project("catalog-a", "Payments");
        var service = service("catalog-a", project.id(), "payments");
        var baseline = baseline("catalog-a", service.id());
        assertEquals("catalog-publisher", baseline.publishedBy());
        assertEquals(document, baseline.document());
        assertEquals(200, request("catalog-a", "GET", "/projects/" + project.id() + "/services", null).statusCode());
        assertTrue(request("catalog-a", "GET", "/services/" + service.id() + "/baselines", null).body().contains("1.0.0"));
        var input = new BaselineAnalysisRequest(document, Map.of());
        assertEquals(200, request("catalog-a", "POST", "/baselines/" + baseline.id() + "/preview", input).statusCode());
        var saved = request("catalog-a", "POST", "/baselines/" + baseline.id() + "/analyses", input);
        assertEquals(201, saved.statusCode(), saved.body());
        var report = json.readValue(saved.body(), SavedAnalysis.class);
        assertEquals(baseline.id(), report.baselineId());
        var loaded = json.readValue(request("catalog-a", "GET", "/analyses/" + report.id(), null).body(), SavedAnalysis.class);
        assertEquals(baseline.id(), loaded.baselineId());
        assertEquals(document, json.readValue(request("catalog-a", "GET", "/snapshots/" + report.beforeSnapshotId(), null).body(), SnapshotResponse.class).document());
        assertEquals(405, request("catalog-a", "PUT", "/baselines/" + baseline.id(), new BaselineRequest("replace", "production", document)).statusCode());
        assertEquals(405, request("catalog-a", "DELETE", "/baselines/" + baseline.id(), null).statusCode());
    }
    @Test void tenantBoundariesApplyToEveryCatalogPath() throws Exception {
        var project = project("catalog-a", "Payments");
        var service = service("catalog-a", project.id(), "payments");
        var baseline = baseline("catalog-a", service.id());
        for (String path : new String[]{"/projects/" + project.id(), "/projects/" + project.id() + "/services",
                "/services/" + service.id(), "/services/" + service.id() + "/baselines", "/baselines/" + baseline.id()})
            assertEquals(404, request("catalog-b", "GET", path, null).statusCode(), path);
        assertEquals("[]", request("catalog-b", "GET", "/projects", null).body());
        assertEquals(404, request("catalog-b", "POST", "/projects/" + project.id() + "/services", new CatalogServiceRequest("other", "team", null)).statusCode());
        assertEquals(404, request("catalog-b", "POST", "/services/" + service.id() + "/baselines", new BaselineRequest("other", "production", document)).statusCode());
        for (String suffix : new String[]{"/preview", "/analyses"})
            assertEquals(404, request("catalog-b", "POST", "/baselines/" + baseline.id() + suffix, new BaselineAnalysisRequest(document, Map.of())).statusCode());
        assertEquals(401, request(null, "GET", "/projects", null).statusCode());
        assertEquals(401, request(null, "POST", "/baselines/" + baseline.id() + "/analyses", new BaselineAnalysisRequest(document, Map.of())).statusCode());
        // Same names may be used in a different tenant.
        service("catalog-b", project("catalog-b", "Payments").id(), "payments");
    }
    @Test void invalidAndDuplicateResourcesHaveClearErrors() throws Exception {
        assertEquals(400, request("catalog-a", "POST", "/projects", new ProjectRequest(" ")).statusCode());
        var project = project("catalog-a", "Payments");
        assertEquals(409, request("catalog-a", "POST", "/projects", new ProjectRequest("Payments")).statusCode());
        assertEquals(400, request("catalog-a", "POST", "/projects/" + project.id() + "/services", new CatalogServiceRequest("bad name", "team", null)).statusCode());
        var service = service("catalog-a", project.id(), "payments");
        assertEquals(409, request("catalog-a", "POST", "/projects/" + project.id() + "/services", new CatalogServiceRequest("payments", "other", null)).statusCode());
        var invalid = new BaselineRequest("1.0.0", "production", Map.of("openapi", "2.0", "paths", Map.of()));
        assertEquals(400, request("catalog-a", "POST", "/services/" + service.id() + "/baselines", invalid).statusCode());
        assertEquals("[]", request("catalog-a", "GET", "/services/" + service.id() + "/baselines", null).body());
        baseline("catalog-a", service.id());
        assertEquals(409, request("catalog-a", "POST", "/services/" + service.id() + "/baselines", new BaselineRequest("1.0.0", "production", document)).statusCode());
        assertEquals(201, request("catalog-a", "POST", "/services/" + service.id() + "/baselines", new BaselineRequest("1.0.0", "staging", document)).statusCode());
        assertEquals(400, request("catalog-a", "GET", "/projects?limit=101", null).statusCode());
        assertEquals(400, request("catalog-a", "GET", "/services/" + service.id() + "/baselines?offset=-1", null).statusCode());
    }
    @Test void proposedContractFailureDoesNotWriteHistoryOrChangeBaseline() throws Exception {
        var service = service("catalog-a", project("catalog-a", "Payments").id(), "payments");
        var baseline = baseline("catalog-a", service.id());
        var invalid = new BaselineAnalysisRequest(Map.of("openapi", "2.0", "paths", Map.of()), Map.of());
        assertEquals(400, request("catalog-a", "POST", "/baselines/" + baseline.id() + "/analyses", invalid).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_report WHERE tenant_id = ?", Integer.class, TENANT_A));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM contract_snapshot WHERE tenant_id = ?", Integer.class, TENANT_A));
        assertEquals(document, json.readValue(request("catalog-a", "GET", "/baselines/" + baseline.id(), null).body(), BaselineResponse.class).document());
    }
    @Test void removedOperationIsComparedAgainstPublishedEvidence() throws Exception {
        var service = service("catalog-a", project("catalog-a", "Payments").id(), "payments");
        var before = Map.<String, Object>of("openapi", "3.0.3", "paths", Map.of("/payments", Map.of("get", Map.of("responses", Map.of("200", Map.of("description", "OK"))))));
        var publication = request("catalog-a", "POST", "/services/" + service.id() + "/baselines", new BaselineRequest("1.0.0", "production", before));
        assertEquals(201, publication.statusCode(), publication.body());
        var baseline = json.readValue(publication.body(), BaselineResponse.class);
        var preview = request("catalog-a", "POST", "/baselines/" + baseline.id() + "/preview",
                new BaselineAnalysisRequest(document, Map.of("orders", java.util.List.of("payments"))));
        assertEquals(200, preview.statusCode(), preview.body());
        assertTrue(preview.body().contains("OPERATION_REMOVED"));
        assertTrue(preview.body().contains("orders"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_report WHERE tenant_id = ?", Integer.class, TENANT_A));
    }
}
