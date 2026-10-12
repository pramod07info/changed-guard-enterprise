package com.changeguard.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.changeguard.dto.*;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.integration.github.GitHubPrClient;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubPrServiceTest {
    private final BaselineService baselines = mock(BaselineService.class);
    private final CatalogService catalog = mock(CatalogService.class);
    private final GitHubPrClient github = mock(GitHubPrClient.class);
    private final UUID id = UUID.randomUUID(), serviceId = UUID.randomUUID();
    private final GitHubPrRequest request = new GitHubPrRequest(7, "openapi.json", Map.of());
    private GitHubPrService service(boolean enabled) {
        return new GitHubPrService(baselines, catalog, github, new DependencyGraphService(), enabled);
    }
    private void catalog() {
        when(baselines.get(id)).thenReturn(new BaselineResponse(id, serviceId, "payments", "1.0", "production", Map.of(), "publisher", Instant.now()));
        when(catalog.service(serviceId)).thenReturn(new CatalogServiceResponse(serviceId, UUID.randomUUID(), "payments", "team", "https://github.com/team/payments", Instant.now()));
    }
    @Test void disabledIntegrationCannotFetch() {
        catalog();
        assertThrows(GitHubIntegrationException.class, () -> service(false).preview(id, request));
        verifyNoInteractions(github);
    }
    @Test void unavailableTenantBaselineIsCheckedBeforeRepositoryAndNetwork() {
        when(baselines.get(id)).thenThrow(new ResourceNotFoundException("Baseline not found"));
        assertThrows(ResourceNotFoundException.class, () -> service(true).preview(id, request));
        verifyNoInteractions(github, catalog);
    }
    @Test void sourceAndFetchedCandidateArePassedToSavedComparison() {
        catalog();
        var source = new GitHubSource("team/payments", "team/payments", 7, "https://github.com/team/payments/pull/7",
                "a".repeat(40), "b".repeat(40), "openapi.json", "c".repeat(40), Instant.now());
        var document = Map.<String, Object>of("openapi", "3.0.3", "paths", Map.of());
        when(github.fetch(any(), eq(7), eq("openapi.json"))).thenReturn(new GitHubPrClient.Candidate(source, document));
        service(true).analyzeAndSave(id, request);
        verify(baselines).analyzeGitHubAndSave(eq(id), argThat(input -> input.after().equals(document)), eq(source));
    }
}
