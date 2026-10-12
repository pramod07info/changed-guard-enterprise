package com.changeguard.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.changeguard.dto.CatalogServiceRequest;
import com.changeguard.dto.CatalogServiceResponse;
import com.changeguard.dto.ProjectRequest;
import com.changeguard.dto.ProjectResponse;
import com.changeguard.entity.CatalogProject;
import com.changeguard.entity.CatalogServiceRecord;
import com.changeguard.exception.CatalogConflictException;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.repository.CatalogRepository;
import com.changeguard.security.TenantContext;
import com.changeguard.utility.CatalogInput;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final CatalogRepository catalog;
    private final TenantContext tenant;
    public CatalogService(CatalogRepository catalog, TenantContext tenant) { this.catalog = catalog; this.tenant = tenant; }
    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        String tenantId = tenant.requiredTenant();
        if (request == null) throw new InvalidContractException("Project request is required");
        var record = new CatalogProject(UUID.randomUUID(), tenantId, CatalogInput.text(request.name(), "name", 200),
                tenant.requiredSubject(), Instant.now());
        try { catalog.insertProject(record); }
        catch (DuplicateKeyException exception) { throw new CatalogConflictException("A project with this name already exists"); }
        return projectResponse(record);
    }
    @Transactional(readOnly = true)
    public List<ProjectResponse> projects(int limit, int offset) {
        String tenantId = tenant.requiredTenant();
        CatalogInput.page(limit, offset);
        return catalog.projects(tenantId, limit, offset).stream().map(this::projectResponse).toList();
    }
    @Transactional(readOnly = true)
    public ProjectResponse project(UUID id) { return projectResponse(requiredProject(tenant.requiredTenant(), id)); }
    @Transactional
    public CatalogServiceResponse createService(UUID projectId, CatalogServiceRequest request) {
        String tenantId = tenant.requiredTenant();
        requiredProject(tenantId, projectId);
        if (request == null) throw new InvalidContractException("Service request is required");
        var record = new CatalogServiceRecord(UUID.randomUUID(), tenantId, projectId,
                CatalogInput.identifier(request.name(), "service name"), CatalogInput.text(request.owner(), "owner", 200),
                CatalogInput.repositoryUrl(request.repositoryUrl()), Instant.now());
        try { catalog.insertService(record); }
        catch (DuplicateKeyException exception) { throw new CatalogConflictException("A service with this name already exists in your tenant"); }
        return serviceResponse(record);
    }
    @Transactional(readOnly = true)
    public List<CatalogServiceResponse> services(UUID projectId, int limit, int offset) {
        String tenantId = tenant.requiredTenant();
        requiredProject(tenantId, projectId);
        CatalogInput.page(limit, offset);
        return catalog.services(tenantId, projectId, limit, offset).stream().map(this::serviceResponse).toList();
    }
    @Transactional(readOnly = true)
    public CatalogServiceResponse service(UUID id) {
        return serviceResponse(catalog.service(tenant.requiredTenant(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found")));
    }
    private CatalogProject requiredProject(String tenantId, UUID id) {
        return catalog.project(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }
    private ProjectResponse projectResponse(CatalogProject record) {
        return new ProjectResponse(record.id(), record.name(), record.createdBy(), record.createdAt());
    }
    private CatalogServiceResponse serviceResponse(CatalogServiceRecord record) {
        return new CatalogServiceResponse(record.id(), record.projectId(), record.name(), record.owner(), record.repositoryUrl(), record.createdAt());
    }
}
