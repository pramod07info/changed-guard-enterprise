package com.changeguard.integration.github;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import com.changeguard.dto.GitHubSource;
import com.changeguard.exception.GitHubIntegrationException;
import com.changeguard.exception.InvalidContractException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GitHubPrClient {
    public record Candidate(GitHubSource source, Map<String, Object> document) {}
    private final GitHubTransport transport;
    private final JsonMapper json;
    public GitHubPrClient(GitHubTransport transport, JsonMapper json) { this.transport = transport; this.json = json; }
    public Candidate fetch(GitHubRepository repository, int number, String contractPath) {
        if (number < 1) throw new InvalidContractException("pullNumber must be positive");
        validatePath(contractPath);
        var pr = payload(transport.get("/repos/" + repository.fullName() + "/pulls/" + number));
        if (!pr.path("number").isIntegralNumber() || !pr.path("number").canConvertToInt() || pr.path("number").asInt() != number
                || !repository.fullName().equalsIgnoreCase(text(pr.path("base").path("repo").path("full_name"))))
            throw upstream();
        if (!"open".equals(text(pr.path("state")))) throw new InvalidContractException("Only open pull requests are supported");
        if (!pr.path("base").path("repo").path("private").isBoolean() || pr.path("base").path("repo").path("private").asBoolean()
                || !pr.path("head").path("repo").path("private").isBoolean() || pr.path("head").path("repo").path("private").asBoolean())
            throw new InvalidContractException("Only public GitHub repositories and public PR heads are supported");
        GitHubRepository head;
        try { head = GitHubRepository.fromFullName(text(pr.path("head").path("repo").path("full_name"))); }
        catch (InvalidContractException exception) { throw upstream(); }
        String baseSha = sha(pr.path("base").path("sha"));
        String headSha = sha(pr.path("head").path("sha"));
        var file = payload(transport.get("/repos/" + head.fullName() + "/contents/" + contractPath + "?ref=" + headSha));
        if (!"file".equals(text(file.path("type"))) || !contractPath.equals(text(file.path("path")))
                || !"base64".equals(text(file.path("encoding"))) || !file.path("size").isIntegralNumber() || !file.path("size").canConvertToLong()
                || file.path("size").asLong() < 0 || file.path("size").asLong() > 1048576)
            throw new InvalidContractException("PR contract must be a regular base64 JSON file no larger than 1 MiB");
        String blobSha = sha(file.path("sha"));
        byte[] decoded;
        String content = text(file.path("content"));
        try { decoded = Base64.getDecoder().decode(content.replaceAll("\\s", "")); }
        catch (IllegalArgumentException exception) { throw upstream(); }
        if (decoded.length > 1048576 || decoded.length != file.path("size").asLong()) throw upstream();
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + decoded.length + "\0").getBytes(StandardCharsets.US_ASCII));
            if (!java.util.HexFormat.of().formatHex(digest.digest(decoded)).equalsIgnoreCase(blobSha)) throw upstream();
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-1 is required for Git blob verification", exception); }
        Map<String, Object> document;
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(decoded)).toString();
            document = json.readerFor(new TypeReference<Map<String, Object>>() {})
                    .with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(text);
            if (document == null) throw new InvalidContractException("PR contract must be a JSON object");
        } catch (InvalidContractException exception) { throw exception; }
        catch (java.nio.charset.CharacterCodingException | tools.jackson.core.JacksonException exception) {
            throw new InvalidContractException("PR contract must contain valid UTF-8 JSON");
        }
        var source = new GitHubSource(repository.fullName(), head.fullName(), number,
                "https://github.com/" + repository.fullName() + "/pull/" + number,
                baseSha, headSha, contractPath, blobSha, Instant.now());
        return new Candidate(source, document);
    }
    public static void validatePath(String path) {
        if (path == null || path.length() > 500 || !path.matches("(?:[A-Za-z0-9_.-]+/)*[A-Za-z0-9_.-]+\\.json"))
            throw new InvalidContractException("contractPath must be a relative .json path using letters, digits, dot, underscore or hyphen");
        for (String segment : path.split("/")) if (segment.equals(".") || segment.equals(".."))
            throw new InvalidContractException("contractPath cannot contain traversal segments");
    }
    private JsonNode payload(byte[] bytes) {
        try { var node = json.readTree(bytes); if (node == null || !node.isObject()) throw upstream(); return node; }
        catch (GitHubIntegrationException exception) { throw exception; }
        catch (tools.jackson.core.JacksonException exception) { throw upstream(); }
    }
    private String sha(JsonNode value) {
        String result = text(value);
        if (result == null || !result.matches("[a-fA-F0-9]{40}")) throw upstream();
        return result;
    }
    private String text(JsonNode value) {
        if (!value.isString()) throw upstream();
        return value.asString();
    }
    private GitHubIntegrationException upstream() { return new GitHubIntegrationException(HttpStatus.BAD_GATEWAY, "GitHub returned invalid repository data"); }
}
