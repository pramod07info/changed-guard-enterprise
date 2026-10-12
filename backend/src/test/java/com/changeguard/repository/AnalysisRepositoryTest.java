package com.changeguard.repository;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import com.changeguard.entity.AnalysisRecord;
import com.changeguard.entity.ContractSnapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AnalysisRepositoryTest {
    @Autowired AnalysisRepository reports;
    @Autowired ContractSnapshotRepository snapshots;

    @Test void jsonbRoundTripAndServicePagination() {
        String service = "repository-test-" + UUID.randomUUID();
        Instant time = Instant.parse("2026-10-11T10:00:00Z");
        UUID before = UUID.randomUUID(), after = UUID.randomUUID(), id = UUID.randomUUID();
        snapshots.insert(new ContractSnapshot(before, "tenant-a", service, time, "{\"openapi\":\"3.0.3\",\"paths\":{}}"));
        snapshots.insert(new ContractSnapshot(after, "tenant-a", service, time, "{\"openapi\":\"3.0.3\",\"paths\":{}}"));
        reports.insert(new AnalysisRecord(id, "tenant-a", service, time, before, after, "{\"risk\":\"NONE\"}", "{}"));
        var saved = reports.findById("tenant-a", id).orElseThrow();
        assertEquals(time, saved.createdAt());
        assertEquals(before, saved.beforeSnapshotId());
        assertTrue(saved.reportJson().contains("NONE"));
        assertTrue(snapshots.findById("tenant-a", before).orElseThrow().documentJson().contains("3.0.3"));
        assertEquals(1, reports.findByService("tenant-a", service, 10, 0).size());
        assertTrue(reports.findByService("tenant-a", service, 10, 1).isEmpty());
        assertTrue(reports.findByService("tenant-a", "missing-service", 10, 0).isEmpty());
    }

    @Test void foreignKeysRejectMissingSnapshots() {
        assertThrows(DataIntegrityViolationException.class, () -> reports.insert(
                new AnalysisRecord(UUID.randomUUID(), "tenant-a", "repository-test", Instant.now(),
                        UUID.randomUUID(), UUID.randomUUID(), "{}", "{}")));
    }

    @Test void missingIdsReturnEmpty() {
        assertTrue(reports.findById("tenant-a", UUID.randomUUID()).isEmpty());
        assertTrue(snapshots.findById("tenant-a", UUID.randomUUID()).isEmpty());
    }

    @Test void databaseRejectsCrossTenantSnapshotLinks() {
        UUID before = UUID.randomUUID(), after = UUID.randomUUID();
        snapshots.insert(new ContractSnapshot(before, "tenant-a", "shared-service", Instant.now(), "{}"));
        snapshots.insert(new ContractSnapshot(after, "tenant-a", "shared-service", Instant.now(), "{}"));
        assertThrows(DataIntegrityViolationException.class, () -> reports.insert(
                new AnalysisRecord(UUID.randomUUID(), "tenant-b", "shared-service", Instant.now(),
                        before, after, "{}", "{}")));
    }
    @Test void sourceEvidenceRequiresABaseline() {
        UUID before = UUID.randomUUID(), after = UUID.randomUUID();
        snapshots.insert(new ContractSnapshot(before, "tenant-a", "payments", Instant.now(), "{}"));
        snapshots.insert(new ContractSnapshot(after, "tenant-a", "payments", Instant.now(), "{}"));
        assertThrows(DataIntegrityViolationException.class, () -> reports.insert(new AnalysisRecord(UUID.randomUUID(), "tenant-a",
                "payments", Instant.now(), before, after, "{}", "{}", null, "{}")));
    }
}
