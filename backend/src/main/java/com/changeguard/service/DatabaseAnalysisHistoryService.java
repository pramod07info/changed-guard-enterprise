package com.changeguard.service;

import com.changeguard.dto.*;
import com.changeguard.entity.DatabaseAnalysisRecord;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.repository.DatabaseAnalysisRepository;
import com.changeguard.security.TenantContext;
import com.changeguard.utility.CatalogInput;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DatabaseAnalysisHistoryService {
  private final DatabaseSchemaService analysis;
  private final DatabaseAnalysisRepository repository;
  private final TenantContext tenant;
  private final JsonMapper json;

  public DatabaseAnalysisHistoryService(
      DatabaseSchemaService analysis,
      DatabaseAnalysisRepository repository,
      TenantContext tenant,
      JsonMapper json) {
    this.analysis = analysis;
    this.repository = repository;
    this.tenant = tenant;
    this.json = json;
  }

  @Transactional
  public SavedDatabaseAnalysis create(DatabaseSchemaRequest request) {
    String owner = tenant.requiredTenant();
    var report = analysis.analyze(request);
    var record =
        new DatabaseAnalysisRecord(
            UUID.randomUUID(),
            owner,
            report.service(),
            Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
            json.writeValueAsString(request.before()),
            json.writeValueAsString(request.after()),
            json.writeValueAsString(
                request.dependencies() == null ? Map.of() : request.dependencies()),
            json.writeValueAsString(report));
    repository.insert(record);
    return response(record);
  }

  @Transactional(readOnly = true)
  public SavedDatabaseAnalysis get(UUID id) {
    return response(
        repository
            .find(tenant.requiredTenant(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Database analysis not found")));
  }

  @Transactional(readOnly = true)
  public List<AnalysisSummary> list(String service, int limit, int offset) {
    String owner = tenant.requiredTenant();
    CatalogInput.page(limit, offset);
    return repository.list(owner, CatalogInput.identifier(service, "service"), limit, offset);
  }

  private SavedDatabaseAnalysis response(DatabaseAnalysisRecord r) {
    return new SavedDatabaseAnalysis(
        r.id(),
        r.service(),
        r.createdAt(),
        json.readValue(r.reportJson(), DatabaseSchemaReport.class),
        json.readValue(r.beforeJson(), new TypeReference<Map<String, Object>>() {}),
        json.readValue(r.afterJson(), new TypeReference<Map<String, Object>>() {}),
        json.readValue(r.dependenciesJson(), new TypeReference<Map<String, List<String>>>() {}));
  }
}
