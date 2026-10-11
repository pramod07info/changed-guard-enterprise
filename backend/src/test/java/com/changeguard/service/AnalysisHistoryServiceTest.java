package com.changeguard.service;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.changeguard.dto.OpenApiRequest;
import com.changeguard.security.WithTenant;
import com.changeguard.entity.AnalysisRecord;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.repository.AnalysisRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@WithTenant
class AnalysisHistoryServiceTest {
    @Autowired AnalysisHistoryService history;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean AnalysisRepository reports;
    private final String service = "history-test-" + UUID.randomUUID();

    private OpenApiRequest request() {
        var before = Map.<String, Object>of("openapi", "3.0.3", "paths", Map.of("/orders", Map.of("get",
                Map.of("responses", Map.of("200", Map.of("description", "OK"))))));
        return new OpenApiRequest(service, before, Map.of("openapi", "3.0.3", "paths", Map.of()),
                Map.of("checkout", java.util.List.of(service)));
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM analysis_report WHERE service = ?", service);
        jdbc.update("DELETE FROM contract_snapshot WHERE service = ?", service);
    }
    @Test void createRetrievesSnapshotsReportAndHistory() {
        var saved = history.create(request());
        var loaded = history.get(saved.id());
        assertEquals(saved.report(), loaded.report());
        assertEquals(saved.beforeSnapshotId(), loaded.beforeSnapshotId());
        assertEquals(request().before(), history.snapshot(saved.beforeSnapshotId()).document());
        assertEquals(request().after(), history.snapshot(saved.afterSnapshotId()).document());
        assertEquals(1, history.list(service, 20, 0).size());
        assertEquals(saved.id(), history.list(service, 20, 0).getFirst().id());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM contract_snapshot WHERE service = ?", Integer.class, service));
    }
    @Test void failedReportInsertRollsBackBothSnapshots() {
        doThrow(new DataIntegrityViolationException("Simulated report insert failure"))
                .when(reports).insert(any(AnalysisRecord.class));
        assertThrows(DataIntegrityViolationException.class, () -> history.create(request()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM contract_snapshot WHERE service = ?", Integer.class, service));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_report WHERE service = ?", Integer.class, service));
    }
    @Test void invalidContractProducesNoWrites() {
        var invalid = new OpenApiRequest(service, Map.of(), Map.of(), null);
        assertThrows(InvalidContractException.class, () -> history.create(invalid));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM contract_snapshot WHERE service = ?", Integer.class, service));
    }
    @Test void missingResourcesAndPaginationValidation() {
        assertThrows(ResourceNotFoundException.class, () -> history.get(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> history.snapshot(UUID.randomUUID()));
        assertThrows(InvalidContractException.class, () -> history.list(service, 101, 0));
        assertThrows(InvalidContractException.class, () -> history.list(service, 0, 0));
        assertThrows(InvalidContractException.class, () -> history.list(service, 10, -1));
        assertThrows(InvalidContractException.class, () -> history.list(" ", 10, 0));
        assertTrue(history.list(service, 20, 0).isEmpty());
    }
}
