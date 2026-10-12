package com.changeguard.integration.github;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.exception.InvalidContractException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubPrClientTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final GitHubTransport transport = mock(GitHubTransport.class);
    private final GitHubPrClient client = new GitHubPrClient(transport, json);
    private final GitHubRepository repository = new GitHubRepository("team", "payments");
    private final String baseSha = "a".repeat(40), headSha = "b".repeat(40);
    private final String document = "{\"openapi\":\"3.0.3\",\"paths\":{}}";
    private Map<String, Object> pr(String headRepository, boolean headPrivate, String state) {
        return Map.of("number", 7, "state", state,
                "base", Map.of("sha", baseSha, "repo", Map.of("full_name", "team/payments", "private", false)),
                "head", Map.of("sha", headSha, "repo", Map.of("full_name", headRepository, "private", headPrivate)),
                "html_url", "http://untrusted.invalid/do-not-follow");
    }
    private Map<String, Object> file(String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var digest = MessageDigest.getInstance("SHA-1"); digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
        return Map.of("type", "file", "path", "api/openapi.json", "encoding", "base64", "size", bytes.length,
                "sha", HexFormat.of().formatHex(digest.digest(bytes)), "content", Base64.getEncoder().encodeToString(bytes));
    }
    private void responses(Map<String, Object> pr, Map<String, Object> file, String head) {
        when(transport.get("/repos/team/payments/pulls/7")).thenReturn(json.writeValueAsBytes(pr));
        when(transport.get("/repos/" + head + "/contents/api/openapi.json?ref=" + headSha)).thenReturn(json.writeValueAsBytes(file));
    }
    @Test void fetchPinsCommitAndReturnsVerifiedSource() throws Exception {
        responses(pr("team/payments", false, "open"), file(document), "team/payments");
        var candidate = client.fetch(repository, 7, "api/openapi.json");
        assertEquals("3.0.3", candidate.document().get("openapi"));
        assertEquals(headSha, candidate.source().headSha());
        assertEquals("https://github.com/team/payments/pull/7", candidate.source().pullUrl());
        verify(transport).get("/repos/team/payments/pulls/7");
        verify(transport).get("/repos/team/payments/contents/api/openapi.json?ref=" + headSha);
        verifyNoMoreInteractions(transport);
    }
    @Test void publicForkHeadUsesItsOwnPinnedRepository() throws Exception {
        responses(pr("contributor/payments", false, "open"), file(document), "contributor/payments");
        var candidate = client.fetch(repository, 7, "api/openapi.json");
        assertEquals("team/payments", candidate.source().repository());
        assertEquals("contributor/payments", candidate.source().headRepository());
    }
    @Test void unsafePathsCannotMakeOutboundRequests() {
        for (String path : new String[]{"../openapi.json", "api/../openapi.json", "/openapi.json", "https://example.com/openapi.json",
                "api/openapi.json?token=x", "api/%2e%2e/openapi.json", "openapi.yaml"})
            assertThrows(InvalidContractException.class, () -> client.fetch(repository, 7, path));
        verifyNoInteractions(transport);
    }
    @Test void privateOrClosedHeadsAreRejected() {
        when(transport.get("/repos/team/payments/pulls/7")).thenReturn(json.writeValueAsBytes(pr("team/payments", true, "open")));
        assertThrows(InvalidContractException.class, () -> client.fetch(repository, 7, "api/openapi.json"));
        when(transport.get("/repos/team/payments/pulls/7")).thenReturn(json.writeValueAsBytes(pr("team/payments", false, "closed")));
        assertThrows(InvalidContractException.class, () -> client.fetch(repository, 7, "api/openapi.json"));
    }
    @Test void missingOrInvalidUpstreamMetadataHasSanitizedFailure() {
        when(transport.get("/repos/team/payments/pulls/7")).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        assertThrows(GitHubIntegrationException.class, () -> client.fetch(repository, 7, "api/openapi.json"));
    }
    @Test void oversizedContractMetadataRejected() throws Exception {
        var file = new HashMap<>(file(document)); file.put("size", 1048577);
        responses(pr("team/payments", false, "open"), file, "team/payments");
        assertThrows(InvalidContractException.class, () -> client.fetch(repository, 7, "api/openapi.json"));
    }
    @Test void invalidBase64AndMissingContentRejected() throws Exception {
        var file = new HashMap<>(file(document)); file.put("content", "%%%invalid");
        responses(pr("team/payments", false, "open"), file, "team/payments");
        assertThrows(GitHubIntegrationException.class, () -> client.fetch(repository, 7, "api/openapi.json"));
        file.remove("content"); responses(pr("team/payments", false, "open"), file, "team/payments");
        assertThrows(GitHubIntegrationException.class, () -> client.fetch(repository, 7, "api/openapi.json"));
    }
    @Test void blobIntegrityMismatchRejected() throws Exception {
        var file = new HashMap<>(file(document)); file.put("sha", "c".repeat(40));
        responses(pr("team/payments", false, "open"), file, "team/payments");
        assertThrows(GitHubIntegrationException.class, () -> client.fetch(repository, 7, "api/openapi.json"));
    }
    @Test void malformedNonObjectOrTrailingContractJsonRejected() throws Exception {
        for (String text : new String[]{"not json", "[]", "null", document + " {}"}) {
            responses(pr("team/payments", false, "open"), file(text), "team/payments");
            assertThrows(InvalidContractException.class, () -> client.fetch(repository, 7, "api/openapi.json"));
        }
    }
    @Test void repositoryUrlsCannotSelectArbitraryHostsOrSubpaths() {
        assertEquals("team/payments", GitHubRepository.fromCatalogUrl("https://github.com/team/payments.git").fullName());
        for (String value : new String[]{"https://gitlab.com/team/payments", "https://github.com/team/payments/tree/main",
                "https://user:secret@github.com/team/payments", "http://localhost/team/payments", "https://github.com/team/payments?x=y"})
            assertThrows(InvalidContractException.class, () -> GitHubRepository.fromCatalogUrl(value));
    }
}
