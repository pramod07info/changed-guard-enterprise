package com.changeguard.repository;

import java.time.Instant;
import java.util.UUID;
import com.changeguard.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CatalogRepositoryTest {
    @Autowired CatalogRepository catalog;
    @Autowired BaselineRepository baselines;
    @Autowired AnalysisRepository analyses;
    @Autowired ContractSnapshotRepository snapshots;
    private final String tenant = "catalog-repo-" + UUID.randomUUID();
    private CatalogProject project() {
        var project = new CatalogProject(UUID.randomUUID(), tenant, "project", "publisher", Instant.now());
        catalog.insertProject(project); return project;
    }
    private CatalogServiceRecord service() {
        var service = new CatalogServiceRecord(UUID.randomUUID(), tenant, project().id(), "payments", "team", null, Instant.now());
        catalog.insertService(service); return service;
    }
    private ContractBaseline baseline(CatalogServiceRecord service) {
        var baseline = new ContractBaseline(UUID.randomUUID(), tenant, service.id(), service.name(), "1.0", "production", "{}", "publisher", Instant.now());
        baselines.insert(baseline); return baseline;
    }
    @Test void repositoryReadsAreTenantScopedAndPaginated() {
        var service = service(); var baseline = baseline(service);
        assertTrue(catalog.project("other-tenant", service.projectId()).isEmpty());
        assertTrue(catalog.service("other-tenant", service.id()).isEmpty());
        assertTrue(catalog.services("other-tenant", service.projectId(), 20, 0).isEmpty());
        assertTrue(baselines.find("other-tenant", baseline.id()).isEmpty());
        assertTrue(baselines.list("other-tenant", service.id(), 20, 0).isEmpty());
        assertEquals(1, catalog.projects(tenant, 20, 0).size());
        assertTrue(catalog.projects(tenant, 20, 1).isEmpty());
        assertEquals(1, baselines.list(tenant, service.id(), 20, 0).size());
        assertTrue(baselines.list(tenant, service.id(), 20, 1).isEmpty());
    }
    @Test void databaseRejectsServiceLinkedToAnotherTenantsProject() {
        var project = project();
        assertThrows(DataIntegrityViolationException.class, () -> catalog.insertService(new CatalogServiceRecord(
                UUID.randomUUID(), "other-tenant", project.id(), "payments", "team", null, Instant.now())));
    }
    @Test void databaseRejectsBaselineLinkedToAnotherTenantsService() {
        var service = service();
        assertThrows(DataIntegrityViolationException.class, () -> baselines.insert(new ContractBaseline(
                UUID.randomUUID(), "other-tenant", service.id(), service.name(), "1.0", "production", "{}", "publisher", Instant.now())));
    }
    @Test void databaseRejectsAnalysisLinkedToWrongServiceBaseline() {
        var service = service(); var baseline = baseline(service);
        UUID before = UUID.randomUUID(), after = UUID.randomUUID();
        snapshots.insert(new ContractSnapshot(before, tenant, "orders", Instant.now(), "{}"));
        snapshots.insert(new ContractSnapshot(after, tenant, "orders", Instant.now(), "{}"));
        assertThrows(DataIntegrityViolationException.class, () -> analyses.insert(new AnalysisRecord(UUID.randomUUID(), tenant,
                "orders", Instant.now(), before, after, "{}", "{}", baseline.id())));
    }
    @Test void databaseRejectsAnalysisLinkedToAnotherTenantsBaseline() {
        var service = service(); var baseline = baseline(service);
        UUID before = UUID.randomUUID(), after = UUID.randomUUID();
        snapshots.insert(new ContractSnapshot(before, "other-tenant", "payments", Instant.now(), "{}"));
        snapshots.insert(new ContractSnapshot(after, "other-tenant", "payments", Instant.now(), "{}"));
        assertThrows(DataIntegrityViolationException.class, () -> analyses.insert(new AnalysisRecord(UUID.randomUUID(), "other-tenant",
                "payments", Instant.now(), before, after, "{}", "{}", baseline.id())));
    }
}
