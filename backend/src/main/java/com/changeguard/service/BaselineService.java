package com.changeguard.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.changeguard.dto.*;
import com.changeguard.entity.ContractBaseline;
import com.changeguard.exception.CatalogConflictException;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.repository.BaselineRepository;
import com.changeguard.repository.CatalogRepository;
import com.changeguard.security.TenantContext;
import com.changeguard.utility.CatalogInput;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class BaselineService {
    private final BaselineRepository baselines;
    private final CatalogRepository catalog;
    private final OpenApiService analysis;
    private final AnalysisHistoryService history;
    private final TenantContext tenant;
    private final JsonMapper json;
    public BaselineService(BaselineRepository baselines, CatalogRepository catalog, OpenApiService analysis,
            AnalysisHistoryService history, TenantContext tenant, JsonMapper json) {
        this.baselines = baselines; this.catalog = catalog; this.analysis = analysis;
        this.history = history; this.tenant = tenant; this.json = json;
    }
    @Transactional
    public BaselineResponse publish(UUID serviceId, BaselineRequest request) {
        String tenantId = tenant.requiredTenant();
        var service = catalog.service(tenantId, serviceId).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        if (request == null) throw new InvalidContractException("Baseline request is required");
        String version = CatalogInput.text(request.version(), "version", 100);
        String environment = CatalogInput.identifier(request.environment(), "environment");
        String document = documentJson(request.document());
        analysis.analyze(new OpenApiRequest(service.name(), request.document(), request.document(), Map.of()));
        var baseline = new ContractBaseline(UUID.randomUUID(), tenantId, serviceId, service.name(), version,
                environment, document, tenant.requiredSubject(), Instant.now());
        try { baselines.insert(baseline); }
        catch (DuplicateKeyException exception) { throw new CatalogConflictException("This service/environment/version baseline already exists; publish a new version"); }
        return response(baseline);
    }
    @Transactional(readOnly = true)
    public List<BaselineSummary> list(UUID serviceId, int limit, int offset) {
        String tenantId = tenant.requiredTenant();
        catalog.service(tenantId, serviceId).orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        CatalogInput.page(limit, offset);
        return baselines.list(tenantId, serviceId, limit, offset);
    }
    @Transactional(readOnly = true)
    public BaselineResponse get(UUID id) { return response(required(id)); }
    @Transactional(readOnly = true)
    public OpenApiReport preview(UUID id, BaselineAnalysisRequest request) {
        return analysis.analyze(comparison(required(id), request));
    }
    @Transactional
    public SavedAnalysis analyzeAndSave(UUID id, BaselineAnalysisRequest request) {
        var baseline = required(id);
        return history.create(comparison(baseline, request), baseline.id());
    }
    @Transactional
    public SavedAnalysis analyzeGitHubAndSave(UUID id, BaselineAnalysisRequest request, GitHubSource source) {
        var baseline = required(id);
        return history.create(comparison(baseline, request), baseline.id(), source);
    }
    private ContractBaseline required(UUID id) {
        return baselines.find(tenant.requiredTenant(), id).orElseThrow(() -> new ResourceNotFoundException("Baseline not found"));
    }
    private OpenApiRequest comparison(ContractBaseline baseline, BaselineAnalysisRequest request) {
        if (request == null) throw new InvalidContractException("Comparison request is required");
        documentJson(request.after());
        return new OpenApiRequest(baseline.serviceName(), document(baseline), request.after(), request.dependencies());
    }
    private String documentJson(Map<String, Object> document) {
        if (document == null) throw new InvalidContractException("Contract document is required");
        String value = json.writeValueAsString(document);
        if (value.getBytes(StandardCharsets.UTF_8).length > 1048576)
            throw new InvalidContractException("Contract document must not exceed 1 MiB");
        return value;
    }
    private Map<String, Object> document(ContractBaseline baseline) {
        return json.readValue(baseline.documentJson(), new TypeReference<Map<String, Object>>() {});
    }
    private BaselineResponse response(ContractBaseline baseline) {
        return new BaselineResponse(baseline.id(), baseline.serviceId(), baseline.serviceName(), baseline.version(),
                baseline.environment(), document(baseline), baseline.publishedBy(), baseline.publishedAt());
    }
}
