package com.changeguard.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.entity.DatabaseAnalysisRecord;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DatabaseAnalysisRepositoryTest {
  @Autowired DatabaseAnalysisRepository repository;

  @Test
  void readsAreTenantScopedAndSummariesExcludeSchemaJson() {
    String tenant = "schema-repo-" + UUID.randomUUID();
    UUID id = UUID.randomUUID();
    repository.insert(
        new DatabaseAnalysisRecord(
            id,
            tenant,
            "payments",
            Instant.now(),
            "{}",
            "{}",
            "{}",
            tools.jackson.databind.json.JsonMapper.builder()
                .build()
                .writeValueAsString(java.util.Map.of("risk", "HIGH", "requiresReview", true))));
    assertTrue(repository.find("other", id).isEmpty());
    assertTrue(repository.list("other", "payments", 20, 0).isEmpty());
    assertEquals(id, repository.find(tenant, id).orElseThrow().id());
    assertEquals(1, repository.list(tenant, "payments", 20, 0).size());
    assertTrue(repository.list(tenant, "payments", 20, 1).isEmpty());
  }
}
