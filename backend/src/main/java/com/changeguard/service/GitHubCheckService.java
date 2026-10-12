package com.changeguard.service;

import com.changeguard.config.GitHubAppSettings;
import com.changeguard.dto.GitHubCheckStatus;
import com.changeguard.exception.CatalogConflictException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.repository.GitHubCheckRepository;
import com.changeguard.security.TenantContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GitHubCheckService {
  private final GitHubCheckRepository checks;
  private final TenantContext tenant;
  private final GitHubAppSettings settings;

  public GitHubCheckService(
      GitHubCheckRepository checks, TenantContext tenant, GitHubAppSettings settings) {
    this.checks = checks;
    this.tenant = tenant;
    this.settings = settings;
  }

  public GitHubCheckStatus status(UUID subscription, UUID delivery) {
    return checks
        .status(tenant.requiredTenant(), subscription, delivery)
        .orElseThrow(() -> new ResourceNotFoundException("Check publication not found"));
  }

  public GitHubCheckStatus retry(UUID subscription, UUID delivery) {
    status(subscription, delivery); // Tenant authorization first, including disabled configuration.
    Long installation = settings.bindings().get(subscription);
    if (!settings.enabled() || installation == null)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "Check binding is not enabled");
    if (!checks.retry(tenant.requiredTenant(), subscription, delivery, installation))
      throw new CatalogConflictException(
          "Only failed publications with the current installation binding can be retried");
    return status(subscription, delivery);
  }
}
