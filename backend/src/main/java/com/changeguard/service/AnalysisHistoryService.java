package com.changeguard.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.changeguard.dto.AnalysisSummary;
import com.changeguard.dto.OpenApiReport;
import com.changeguard.dto.OpenApiRequest;
import com.changeguard.dto.SavedAnalysis;
import com.changeguard.dto.SnapshotResponse;
import com.changeguard.entity.AnalysisRecord;
import com.changeguard.entity.ContractSnapshot;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.repository.AnalysisRepository;
import com.changeguard.security.TenantContext;
import com.changeguard.repository.ContractSnapshotRepository;

@Service
public class AnalysisHistoryService {
    private final OpenApiService analysis;
    private final AnalysisRepository reports;
    private final ContractSnapshotRepository snapshots;
    private final JsonMapper json;
    private final TenantContext tenant;
    public AnalysisHistoryService(OpenApiService analysis, AnalysisRepository reports,
            ContractSnapshotRepository snapshots, JsonMapper json, TenantContext tenant) {
        this.analysis = analysis;
        this.reports = reports;
        this.snapshots = snapshots;
        this.json = json;
        this.tenant = tenant;
    }

    @Transactional
    public SavedAnalysis create(OpenApiRequest request) {
        String tenantId = tenant.requiredTenant();
        if (request == null) throw new InvalidContractException("request is required");
        validateService(request.service());
        OpenApiReport report = analysis.analyze(request);
        Instant now = Instant.now();
        UUID beforeId = UUID.randomUUID(), afterId = UUID.randomUUID(), id = UUID.randomUUID();
        snapshots.insert(new ContractSnapshot(beforeId, tenantId, request.service(), now, json.writeValueAsString(request.before())));
        snapshots.insert(new ContractSnapshot(afterId, tenantId, request.service(), now, json.writeValueAsString(request.after())));
        reports.insert(new AnalysisRecord(id, tenantId, request.service(), now, beforeId, afterId,
                json.writeValueAsString(report),
                json.writeValueAsString(request.dependencies() == null ? Map.of() : request.dependencies())));
        return new SavedAnalysis(id, request.service(), now, beforeId, afterId, report);
    }

    @Transactional(readOnly = true)
    public SavedAnalysis get(UUID id) {
        return toResponse(reports.findById(tenant.requiredTenant(), id).orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<AnalysisSummary> list(String service, int limit, int offset) {
        validateService(service);
        if (limit < 1 || limit > 100 || offset < 0 || offset > 100000)
            throw new InvalidContractException("limit must be 1..100 and offset must be 0..100000");
        return reports.findByService(tenant.requiredTenant(), service, limit, offset).stream().map(record -> {
            var report = json.readValue(record.reportJson(), OpenApiReport.class);
            return new AnalysisSummary(record.id(), record.service(), record.createdAt(), report.risk(), report.requiresReview());
        }).toList();
    }

    @Transactional(readOnly = true)
    public SnapshotResponse snapshot(UUID id) {
        var snapshot = snapshots.findById(tenant.requiredTenant(), id).orElseThrow(() -> new ResourceNotFoundException("Snapshot not found: " + id));
        Map<String, Object> document = json.readValue(snapshot.documentJson(), new TypeReference<Map<String, Object>>() {});
        return new SnapshotResponse(snapshot.id(), snapshot.service(), snapshot.createdAt(), document);
    }
    private SavedAnalysis toResponse(AnalysisRecord record) {
        return new SavedAnalysis(record.id(), record.service(), record.createdAt(),
                record.beforeSnapshotId(), record.afterSnapshotId(), json.readValue(record.reportJson(), OpenApiReport.class));
    }
    private void validateService(String service) {
        if (service == null || service.isBlank() || service.length() > 200)
            throw new InvalidContractException("service must contain 1..200 characters and cannot be blank");
    }
}
