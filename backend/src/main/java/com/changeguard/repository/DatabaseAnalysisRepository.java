package com.changeguard.repository;

import com.changeguard.dto.AnalysisSummary;
import com.changeguard.dto.ImpactReport;
import com.changeguard.entity.DatabaseAnalysisRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DatabaseAnalysisRepository {
  private final JdbcTemplate jdbc;

  public DatabaseAnalysisRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(DatabaseAnalysisRecord r) {
    jdbc.update(
        "INSERT INTO database_schema_analysis"
            + " (id,tenant_id,service,created_at,before_schema,after_schema,dependencies,report)"
            + " VALUES (?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS"
            + " jsonb))",
        r.id(),
        r.tenantId(),
        r.service(),
        OffsetDateTime.ofInstant(r.createdAt(), ZoneOffset.UTC),
        r.beforeJson(),
        r.afterJson(),
        r.dependenciesJson(),
        r.reportJson());
  }

  public Optional<DatabaseAnalysisRecord> find(String tenant, UUID id) {
    return jdbc
        .query(
            "SELECT *,before_schema::text AS before_json,after_schema::text AS"
                + " after_json,dependencies::text AS dependencies_json,report::text AS report_json"
                + " FROM database_schema_analysis WHERE tenant_id=? AND id=?",
            (rs, row) ->
                new DatabaseAnalysisRecord(
                    id,
                    rs.getString("tenant_id"),
                    rs.getString("service"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                    rs.getString("before_json"),
                    rs.getString("after_json"),
                    rs.getString("dependencies_json"),
                    rs.getString("report_json")),
            tenant,
            id)
        .stream()
        .findFirst();
  }

  public List<AnalysisSummary> list(String tenant, String service, int limit, int offset) {
    return jdbc.query(
        "SELECT id,service,created_at,report->>'risk' AS risk,(report->>'requiresReview')::boolean"
            + " AS review FROM database_schema_analysis WHERE tenant_id=? AND service=? ORDER BY"
            + " created_at DESC,id DESC LIMIT ? OFFSET ?",
        (rs, row) ->
            new AnalysisSummary(
                rs.getObject("id", UUID.class),
                rs.getString("service"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                ImpactReport.Risk.valueOf(rs.getString("risk")),
                rs.getBoolean("review")),
        tenant,
        service,
        limit,
        offset);
  }
}
