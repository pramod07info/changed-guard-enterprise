package com.changeguard.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.changeguard.entity.AnalysisRecord;

@Repository
public class AnalysisRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = """
            SELECT id, tenant_id, service, created_at, before_snapshot_id, after_snapshot_id,
                report::text, dependencies::text FROM analysis_report
            """;
    private static final RowMapper<AnalysisRecord> MAPPER = (rs, row) -> new AnalysisRecord(
            rs.getObject("id", UUID.class), rs.getString("tenant_id"), rs.getString("service"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("before_snapshot_id", UUID.class), rs.getObject("after_snapshot_id", UUID.class),
            rs.getString("report"), rs.getString("dependencies"));
    public AnalysisRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(AnalysisRecord record) {
        jdbc.update("""
                INSERT INTO analysis_report
                  (id, tenant_id, service, created_at, before_snapshot_id, after_snapshot_id, report, dependencies)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))
                """, record.id(), record.tenantId(), record.service(), OffsetDateTime.ofInstant(record.createdAt(), ZoneOffset.UTC),
                record.beforeSnapshotId(), record.afterSnapshotId(), record.reportJson(), record.dependenciesJson());
    }
    public Optional<AnalysisRecord> findById(String tenantId, UUID id) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND id = ?", MAPPER, tenantId, id).stream().findFirst();
    }
    public List<AnalysisRecord> findByService(String tenantId, String service, int limit, int offset) {
        return jdbc.query(SELECT + " WHERE tenant_id = ? AND service = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, tenantId, service, limit, offset);
    }
}
