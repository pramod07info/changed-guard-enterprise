package com.changeguard.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.changeguard.dto.BaselineRequest;
import com.changeguard.dto.BaselineAnalysisRequest;
import com.changeguard.entity.CatalogServiceRecord;
import com.changeguard.entity.ContractBaseline;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.repository.BaselineRepository;
import com.changeguard.repository.CatalogRepository;
import com.changeguard.security.TenantContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaselineServiceTest {
    private final BaselineRepository baselines = mock(BaselineRepository.class);
    private final CatalogRepository catalog = mock(CatalogRepository.class);
    private final AnalysisHistoryService history = mock(AnalysisHistoryService.class);
    private final TenantContext tenant = mock(TenantContext.class);
    private final BaselineService service = new BaselineService(baselines, catalog,
            new OpenApiService(new DependencyGraphService()), history, tenant, JsonMapper.builder().build());
    private final UUID serviceId = UUID.randomUUID();
    private final Map<String, Object> document = Map.of("openapi", "3.0.3", "paths", Map.of());
    BaselineServiceTest() {
        when(tenant.requiredTenant()).thenReturn("tenant-a");
        when(tenant.requiredSubject()).thenReturn("publisher");
        when(catalog.service("tenant-a", serviceId)).thenReturn(Optional.of(
                new CatalogServiceRecord(serviceId, "tenant-a", UUID.randomUUID(), "payments", "team", null, Instant.now())));
    }
    @Test void publicationValidatesContractAndRecordsPublisher() {
        var baseline = service.publish(serviceId, new BaselineRequest("1.0", "production", document));
        assertEquals("publisher", baseline.publishedBy());
        verify(baselines).insert(argThat(record -> record.tenantId().equals("tenant-a") && record.serviceName().equals("payments")));
    }
    @Test void unsupportedContractCannotBePublished() {
        assertThrows(InvalidContractException.class, () -> service.publish(serviceId,
                new BaselineRequest("1.0", "production", Map.of("openapi", "2.0", "paths", Map.of()))));
        verify(baselines, never()).insert(any());
    }
    @Test void oversizedDocumentCannotBePublished() {
        assertThrows(InvalidContractException.class, () -> service.publish(serviceId,
                new BaselineRequest("1.0", "production", Map.of("openapi", "3.0.3", "description", "x".repeat(1048576), "paths", Map.of()))));
        verify(baselines, never()).insert(any());
    }
    @Test void comparisonUsesStoredContractAndIgnoresClientBaselineChoices() {
        UUID id = UUID.randomUUID();
        when(baselines.find("tenant-a", id)).thenReturn(Optional.of(new ContractBaseline(id, "tenant-a", serviceId,
                "payments", "1.0", "production", "{\"openapi\":\"3.0.3\",\"paths\":{}}", "publisher", Instant.now())));
        service.analyzeAndSave(id, new BaselineAnalysisRequest(document, Map.of()));
        verify(history).create(argThat(input -> input.service().equals("payments") && input.before().equals(document)), eq(id));
    }
    @Test void unavailableBaselineCannotAnalyzeOrWrite() {
        UUID id = UUID.randomUUID(); when(baselines.find("tenant-a", id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.analyzeAndSave(id, new BaselineAnalysisRequest(document, Map.of())));
        verifyNoInteractions(history);
    }
}
