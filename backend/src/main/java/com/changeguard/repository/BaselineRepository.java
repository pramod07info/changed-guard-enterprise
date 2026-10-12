package com.changeguard.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.changeguard.dto.BaselineSummary;
import com.changeguard.entity.ContractBaseline;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BaselineRepository {
    private final JdbcTemplate jdbc;
    public BaselineRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void insert(ContractBaseline baseline) {
        jdbc.update("""
                INSERT INTO contract_baseline (id, tenant_id, service_id, service_name, version, environment,
                    document, published_by, published_at) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """, baseline.id(), baseline.tenantId(), baseline.serviceId(), baseline.serviceName(), baseline.version(),
                baseline.environment(), baseline.documentJson(), baseline.publishedBy(),
                OffsetDateTime.ofInstant(baseline.publishedAt(), ZoneOffset.UTC));
    }
    public Optional<ContractBaseline> find(String tenant, UUID id) {
        return jdbc.query("SELECT *, document::text AS document_json FROM contract_baseline WHERE tenant_id = ? AND id = ?",
                (rs, row) -> new ContractBaseline(rs.getObject("id", UUID.class), rs.getString("tenant_id"),
                        rs.getObject("service_id", UUID.class), rs.getString("service_name"), rs.getString("version"),
                        rs.getString("environment"), rs.getString("document_json"), rs.getString("published_by"),
                        rs.getObject("published_at", OffsetDateTime.class).toInstant()), tenant, id).stream().findFirst();
    }
    /** Read projection deliberately excludes potentially large documents. */
    public List<BaselineSummary> list(String tenant, UUID service, int limit, int offset) {
        return jdbc.query("""
                SELECT id, service_id, service_name, version, environment, published_by, published_at
                FROM contract_baseline WHERE tenant_id = ? AND service_id = ?
                ORDER BY published_at DESC, id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new BaselineSummary(rs.getObject("id", UUID.class), rs.getObject("service_id", UUID.class),
                        rs.getString("service_name"), rs.getString("version"), rs.getString("environment"),
                        rs.getString("published_by"), rs.getObject("published_at", OffsetDateTime.class).toInstant()),
                tenant, service, limit, offset);
    }
}
