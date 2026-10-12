package com.changeguard.integration.github;

import com.changeguard.config.GitHubAppSettings;
import com.changeguard.entity.GitHubCheckJob;
import com.changeguard.exception.GitHubIntegrationException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GitHubChecksClient {
  private final GitHubAppSettings settings;
  private final GitHubAppJwt signer;
  private final GitHubAppTransport api;
  private final JsonMapper json;

  public GitHubChecksClient(
      GitHubAppSettings settings, GitHubAppJwt signer, GitHubAppTransport api, JsonMapper json) {
    this.settings = settings;
    this.signer = signer;
    this.api = api;
    this.json = json;
  }

  public long publish(GitHubCheckJob job, String conclusion, String summary) {
    if (!settings.approved(job.subscriptionId(), job.installationId())) throw invalid();
    if (!List.of("success", "failure").contains(conclusion)
        || summary.length() > 50000
        || !job.headSha().matches("[a-f0-9]{40}")) throw invalid();
    var repo = GitHubRepository.fromFullName(job.repository());
    String prefix = "/repos/" + repo.fullName();
    String jwt = signer.token();
    var installation = payload(api.request("GET", prefix + "/installation", jwt, null));
    if (positive(installation.path("id")) != job.installationId()
        || positive(installation.path("app_id")) != settings.appId()) throw invalid();
    // Repository identity is independently fetched from the fixed GitHub origin; body IDs are not
    // trusted.
    var metadata = payload(api.request("GET", prefix, null, null));
    long repoId = positive(metadata.path("id"));
    if (!repo.fullName().equalsIgnoreCase(metadata.path("full_name").asText())
        || !metadata.path("private").isBoolean()
        || metadata.path("private").asBoolean()) throw invalid();
    var minted =
        payload(
            api.request(
                "POST",
                "/app/installations/" + job.installationId() + "/access_tokens",
                jwt,
                Map.of(
                    "repository_ids", List.of(repoId), "permissions", Map.of("checks", "write"))));
    String token = minted.path("token").asText();
    try {
      if (!minted.path("token").isString()
          || token.isBlank()
          || token.length() > 16384
          || token.chars().anyMatch(Character::isWhitespace)
          || !Instant.parse(minted.path("expires_at").asText())
              .isAfter(Instant.now().plusSeconds(60))
          || !"write".equals(minted.path("permissions").path("checks").asText())) throw invalid();
    } catch (java.time.format.DateTimeParseException e) {
      throw invalid();
    }
    String name = GitHubAppSettings.checkName(job.subscriptionId());
    String listPath =
        prefix
            + "/commits/"
            + job.headSha()
            + "/check-runs?check_name="
            + URLEncoder.encode(name, StandardCharsets.UTF_8)
            + "&filter=all&per_page=100";
    var existing = payload(api.request("GET", listPath, token, null));
    var runs = existing.path("check_runs");
    if (!runs.isArray()
        || !existing.path("total_count").isIntegralNumber()
        || !existing.path("total_count").canConvertToInt()
        || existing.path("total_count").asInt() < 0
        || existing.path("total_count").asInt() > 100
        || runs.size() != existing.path("total_count").asInt()) throw invalid();
    Long found = null;
    for (var run : runs) {
      if (job.externalId().equals(run.path("external_id").asText())
          && run.path("app").path("id").isIntegralNumber()
          && run.path("app").path("id").asLong() == settings.appId()) {
        if (found != null
            || !name.equals(run.path("name").asText())
            || !job.headSha().equalsIgnoreCase(run.path("head_sha").asText())) throw invalid();
        found = positive(run.path("id"));
      }
    }
    Map<String, Object> body =
        Map.of(
            "name",
            name,
            "head_sha",
            job.headSha(),
            "external_id",
            job.externalId(),
            "status",
            "completed",
            "conclusion",
            conclusion,
            "completed_at",
            Instant.now().toString(),
            "output",
            Map.of(
                "title",
                conclusion.equals("success")
                    ? "Supported API checks passed"
                    : "API review or analysis action required",
                "summary",
                summary));
    var update = new java.util.HashMap<>(body);
    if (found != null) update.remove("head_sha"); // GitHub check heads are immutable on PATCH.
    var saved =
        payload(
            api.request(
                found == null ? "POST" : "PATCH",
                prefix + "/check-runs" + (found == null ? "" : "/" + found),
                token,
                update));
    long id = positive(saved.path("id"));
    if ((found != null && id != found)
        || !name.equals(saved.path("name").asText())
        || !job.headSha().equalsIgnoreCase(saved.path("head_sha").asText())
        || !job.externalId().equals(saved.path("external_id").asText())
        || positive(saved.path("app").path("id")) != settings.appId()
        || !"completed".equals(saved.path("status").asText())
        || !conclusion.equals(saved.path("conclusion").asText())) throw invalid();
    return id;
  }

  private long positive(JsonNode value) {
    if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 1)
      throw invalid();
    return value.asLong();
  }

  private JsonNode payload(byte[] bytes) {
    try {
      var node = json.readTree(bytes);
      if (node == null || !node.isObject()) throw invalid();
      return node;
    } catch (tools.jackson.core.JacksonException e) {
      throw invalid();
    }
  }

  private GitHubIntegrationException invalid() {
    return new GitHubIntegrationException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "GitHub check identity or authorization could not be verified");
  }
}
