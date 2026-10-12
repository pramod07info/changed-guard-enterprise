package com.changeguard.service;

import com.changeguard.dto.*;
import com.changeguard.entity.GitHubWebhookSubscription;
import com.changeguard.exception.CatalogConflictException;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.integration.github.GitHubPrClient;
import com.changeguard.integration.github.GitHubRepository;
import com.changeguard.repository.GitHubWebhookRepository;
import com.changeguard.security.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GitHubWebhookService {
  private final GitHubWebhookSignature signature;
  private final GitHubWebhookRepository repository;
  private final BaselineService baselines;
  private final CatalogService catalog;
  private final DependencyGraphService graph;
  private final TenantContext tenant;
  private final JsonMapper json;

  public GitHubWebhookService(
      GitHubWebhookSignature signature,
      GitHubWebhookRepository repository,
      BaselineService baselines,
      CatalogService catalog,
      DependencyGraphService graph,
      TenantContext tenant,
      JsonMapper json) {
    this.signature = signature;
    this.repository = repository;
    this.baselines = baselines;
    this.catalog = catalog;
    this.graph = graph;
    this.tenant = tenant;
    this.json = json;
  }

  @Transactional
  public GitHubWebhookResponse create(UUID baselineId, GitHubWebhookRequest request) {
    signature.requireEnabled();
    var baseline = baselines.get(baselineId);
    if (request == null) throw new InvalidContractException("Webhook request is required");
    GitHubPrClient.validatePath(request.contractPath());
    graph.validate(baseline.serviceName(), request.dependencies());
    String repo =
        GitHubRepository.fromCatalogUrl(catalog.service(baseline.serviceId()).repositoryUrl())
            .fullName();
    var sub =
        new GitHubWebhookSubscription(
            UUID.randomUUID(),
            tenant.requiredTenant(),
            baselineId,
            repo,
            request.contractPath(),
            json.writeValueAsString(
                request.dependencies() == null ? Map.of() : request.dependencies()),
            tenant.requiredSubject());
    try {
      repository.insert(sub);
    } catch (DuplicateKeyException e) {
      throw new CatalogConflictException(
          "A webhook already exists for this baseline and contract path");
    }
    return response(sub);
  }

  public List<GitHubWebhookResponse> list(UUID baseline) {
    baselines.get(baseline);
    return repository.list(tenant.requiredTenant(), baseline).stream().map(this::response).toList();
  }

  public List<GitHubWebhookDeliverySummary> deliveries(UUID subscription, int limit, int offset) {
    String tenantId = tenant.requiredTenant();
    repository
        .find(tenantId, subscription)
        .orElseThrow(() -> new ResourceNotFoundException("Webhook not found"));
    com.changeguard.utility.CatalogInput.page(limit, offset);
    return repository.deliveries(tenantId, subscription, limit, offset);
  }

  public GitHubWebhookDelivery status(UUID subscription, UUID delivery) {
    return repository
        .delivery(tenant.requiredTenant(), subscription, delivery)
        .orElseThrow(() -> new ResourceNotFoundException("Delivery not found"));
  }

  @Transactional
  public Map<String, String> receive(
      UUID id, String deliveryHeader, String event, String mac, byte[] body) {
    if (body.length > 1048576)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook body exceeds 1 MiB");
    signature.verify(
        body, mac); // Authenticate the exact raw bytes before parsing or looking up tenant data.
    var sub =
        repository
            .ingress(id)
            .orElseThrow(() -> new ResourceNotFoundException("Webhook not found"));
    UUID delivery;
    try {
      if (deliveryHeader == null
          || !deliveryHeader.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))
        throw new IllegalArgumentException();
      delivery = UUID.fromString(deliveryHeader);
    } catch (IllegalArgumentException e) {
      throw new InvalidContractException("X-GitHub-Delivery must be a UUID");
    }
    if (!"pull_request".equals(event) && !"ping".equals(event)) return Map.of("status", "ignored");
    tools.jackson.databind.JsonNode payload;
    try {
      payload =
          json.reader()
              .with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
              .readTree(body);
    } catch (tools.jackson.core.JacksonException e) {
      throw new InvalidContractException("Webhook must contain valid JSON");
    }
    if (payload == null || !payload.isObject())
      throw new InvalidContractException("Webhook must contain a JSON object");
    if ("ping".equals(event)) return Map.of("status", "pong");
    if (!Set.of("opened", "reopened", "synchronize", "ready_for_review")
        .contains(payload.path("action").asText())) return Map.of("status", "ignored");
    var pr = payload.path("pull_request");
    String repo = payload.path("repository").path("full_name").asText();
    String base = pr.path("base").path("repo").path("full_name").asText();
    var number = payload.path("number");
    String sha = pr.path("head").path("sha").asText();
    if (!sub.repository().equalsIgnoreCase(repo)
        || !sub.repository().equalsIgnoreCase(base)
        || !number.isIntegralNumber()
        || !number.canConvertToInt()
        || number.asInt() < 1
        || !pr.path("number").isIntegralNumber()
        || !pr.path("number").canConvertToInt()
        || pr.path("number").asInt() != number.asInt()
        || !"open".equals(pr.path("state").asText())
        || !sha.matches("[a-fA-F0-9]{40}"))
      throw new InvalidContractException(
          "Webhook PR does not match the registered repository or has invalid metadata");
    String hash;
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(event.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      hash = HexFormat.of().formatHex(digest.digest(body));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    boolean added =
        repository.enqueue(
            sub, delivery, hash, number.asInt(), sha.toLowerCase(java.util.Locale.ROOT));
    if (!added && !hash.equals(repository.hash(sub.tenantId(), id, delivery).orElseThrow()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Delivery ID was reused with different content");
    return Map.of("status", added ? "queued" : "duplicate");
  }

  private GitHubWebhookResponse response(GitHubWebhookSubscription s) {
    return new GitHubWebhookResponse(
        s.id(),
        s.baselineId(),
        s.repository(),
        s.contractPath(),
        "/api/v1/github/webhooks/" + s.id() + "/deliveries");
  }
}
