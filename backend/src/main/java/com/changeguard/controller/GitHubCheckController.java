package com.changeguard.controller;

import com.changeguard.dto.GitHubCheckStatus;
import com.changeguard.service.GitHubCheckService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/github/webhooks/{subscription}/deliveries/{delivery}/check")
public class GitHubCheckController {
  private final GitHubCheckService service;

  public GitHubCheckController(GitHubCheckService service) {
    this.service = service;
  }

  @GetMapping
  public GitHubCheckStatus status(@PathVariable UUID subscription, @PathVariable UUID delivery) {
    return service.status(subscription, delivery);
  }

  @PostMapping("/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public GitHubCheckStatus retry(@PathVariable UUID subscription, @PathVariable UUID delivery) {
    return service.retry(subscription, delivery);
  }
}
