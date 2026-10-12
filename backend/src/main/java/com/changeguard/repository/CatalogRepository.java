package com.changeguard.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.changeguard.entity.CatalogProject;
import com.changeguard.entity.CatalogServiceRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<CatalogProject> PROJECT = (rs, row) -> new CatalogProject(
            rs.getObject("id", UUID.class), rs.getString("tenant_id"), rs.getString("name"),
            rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    private static final RowMapper<CatalogServiceRecord> SERVICE = (rs, row) -> new CatalogServiceRecord(
            rs.getObject("id", UUID.class), rs.getString("tenant_id"), rs.getObject("project_id", UUID.class),
            rs.getString("name"), rs.getString("owner"), rs.getString("repository_url"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());
    public CatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void insertProject(CatalogProject project) {
        jdbc.update("INSERT INTO catalog_project (id, tenant_id, name, created_by, created_at) VALUES (?, ?, ?, ?, ?)",
                project.id(), project.tenantId(), project.name(), project.createdBy(),
                OffsetDateTime.ofInstant(project.createdAt(), ZoneOffset.UTC));
    }
    public Optional<CatalogProject> project(String tenant, UUID id) {
        return jdbc.query("SELECT * FROM catalog_project WHERE tenant_id = ? AND id = ?", PROJECT, tenant, id).stream().findFirst();
    }
    public List<CatalogProject> projects(String tenant, int limit, int offset) {
        return jdbc.query("SELECT * FROM catalog_project WHERE tenant_id = ? ORDER BY name, id LIMIT ? OFFSET ?",
                PROJECT, tenant, limit, offset);
    }
    public void insertService(CatalogServiceRecord service) {
        jdbc.update("INSERT INTO catalog_service (id, tenant_id, project_id, name, owner, repository_url, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                service.id(), service.tenantId(), service.projectId(), service.name(), service.owner(), service.repositoryUrl(),
                OffsetDateTime.ofInstant(service.createdAt(), ZoneOffset.UTC));
    }
    public Optional<CatalogServiceRecord> service(String tenant, UUID id) {
        return jdbc.query("SELECT * FROM catalog_service WHERE tenant_id = ? AND id = ?", SERVICE, tenant, id).stream().findFirst();
    }
    public List<CatalogServiceRecord> services(String tenant, UUID project, int limit, int offset) {
        return jdbc.query("SELECT * FROM catalog_service WHERE tenant_id = ? AND project_id = ? ORDER BY name, id LIMIT ? OFFSET ?",
                SERVICE, tenant, project, limit, offset);
    }
}
