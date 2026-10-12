package com.changeguard.service;

import java.util.Optional;
import java.util.UUID;
import com.changeguard.dto.ProjectRequest;
import com.changeguard.dto.CatalogServiceRequest;
import com.changeguard.exception.CatalogConflictException;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.exception.ResourceNotFoundException;
import com.changeguard.repository.CatalogRepository;
import com.changeguard.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final CatalogRepository repository = mock(CatalogRepository.class);
    private final TenantContext tenant = mock(TenantContext.class);
    private final CatalogService catalog = new CatalogService(repository, tenant);
    CatalogServiceTest() {
        when(tenant.requiredTenant()).thenReturn("tenant-a");
        when(tenant.requiredSubject()).thenReturn("publisher-a");
    }
    @Test void projectRecordsTrustedIdentityAndNormalizesName() {
        var project = catalog.createProject(new ProjectRequest(" Payments team "));
        assertEquals("Payments team", project.name());
        assertEquals("publisher-a", project.createdBy());
        verify(repository).insertProject(argThat(record -> record.tenantId().equals("tenant-a")));
    }
    @Test void invalidNameCannotWrite() {
        assertThrows(InvalidContractException.class, () -> catalog.createProject(new ProjectRequest(" ")));
        verify(repository, never()).insertProject(any());
    }
    @Test void duplicateProjectIsConflict() {
        doThrow(new DuplicateKeyException("test")).when(repository).insertProject(any());
        assertThrows(CatalogConflictException.class, () -> catalog.createProject(new ProjectRequest("payments")));
    }
    @Test void missingParentCannotCreateService() {
        UUID id = UUID.randomUUID(); when(repository.project("tenant-a", id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> catalog.createService(id, new CatalogServiceRequest("payments", "team", null)));
        verify(repository, never()).insertService(any());
    }
    @Test void paginationIsBounded() {
        assertThrows(InvalidContractException.class, () -> catalog.projects(101, 0));
        assertThrows(InvalidContractException.class, () -> catalog.projects(20, -1));
        verify(repository, never()).projects(anyString(), anyInt(), anyInt());
    }
}
