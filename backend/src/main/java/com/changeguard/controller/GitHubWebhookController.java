package com.changeguard.controller;

import com.changeguard.dto.*;
import com.changeguard.service.GitHubWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class GitHubWebhookController {
  private final GitHubWebhookService service;

  public GitHubWebhookController(GitHubWebhookService service) {
    this.service = service;
  }

  @PostMapping("/baselines/{id}/github-webhooks")
  @ResponseStatus(HttpStatus.CREATED)
  public GitHubWebhookResponse create(
      @PathVariable UUID id, @RequestBody GitHubWebhookRequest request) {
    return service.create(id, request);
  }

  @GetMapping("/baselines/{id}/github-webhooks")
  public List<GitHubWebhookResponse> list(@PathVariable UUID id) {
    return service.list(id);
  }

  @PostMapping(value = "/github/webhooks/{id}/deliveries", consumes = "application/json")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public Map<String, String> receive(@PathVariable UUID id, HttpServletRequest request)
      throws IOException {
    if (request.getContentLengthLong() > 1048576)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook body exceeds 1 MiB");
    byte[] bytes = request.getInputStream().readNBytes(1048577);
    return service.receive(
        id,
        request.getHeader("X-GitHub-Delivery"),
        request.getHeader("X-GitHub-Event"),
        request.getHeader("X-Hub-Signature-256"),
        bytes);
  }

  @GetMapping("/github/webhooks/{id}/deliveries")
  public List<GitHubWebhookDeliverySummary> deliveries(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return service.deliveries(id, limit, offset);
  }

  @GetMapping("/github/webhooks/{id}/deliveries/{delivery}")
  public GitHubWebhookDelivery status(@PathVariable UUID id, @PathVariable UUID delivery) {
    return service.status(id, delivery);
  }
}
