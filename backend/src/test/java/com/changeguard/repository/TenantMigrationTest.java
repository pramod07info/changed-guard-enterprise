package com.changeguard.repository;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class TenantMigrationTest {
    @Autowired DataSource dataSource;
    @Test void upgradeQuarantinesExistingUnownedHistory() {
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load().migrate();
            UUID before = UUID.randomUUID(), after = UUID.randomUUID();
            jdbc.update("INSERT INTO " + schema + ".contract_snapshot VALUES (?, 'legacy', now(), '{}'::jsonb)", before);
            jdbc.update("INSERT INTO " + schema + ".contract_snapshot VALUES (?, 'legacy', now(), '{}'::jsonb)", after);
            jdbc.update("INSERT INTO " + schema + ".analysis_report VALUES (?, 'legacy', now(), ?, ?, '{}'::jsonb, '{}'::jsonb)",
                    UUID.randomUUID(), before, after);
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertEquals("__legacy_unassigned__", jdbc.queryForObject(
                    "SELECT tenant_id FROM " + schema + ".analysis_report", String.class));
            assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM " + schema
                    + ".contract_snapshot WHERE tenant_id = '__legacy_unassigned__'", Integer.class));
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
