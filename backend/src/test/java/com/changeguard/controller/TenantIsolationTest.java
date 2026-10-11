package com.changeguard.controller;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.changeguard.dto.OpenApiRequest;
import com.changeguard.dto.SavedAnalysis;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantIsolationTest {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Test void crossTenantReportSnapshotAndListAreHidden() throws Exception {
        String service = "isolation-" + UUID.randomUUID();
        var document = Map.<String, Object>of("openapi", "3.0.3", "paths", Map.of());
        var created = mvc.perform(post("/api/v1/analyses")
                .with(jwt().jwt(j -> j.subject("user-a").claim("tenant_id", "tenant-a")))
                .contentType("application/json")
                .content(json.writeValueAsString(new OpenApiRequest(service, document, document, null))))
                .andExpect(status().isCreated()).andReturn();
        var saved = json.readValue(created.getResponse().getContentAsString(), SavedAnalysis.class);
        mvc.perform(get("/api/v1/analyses/" + saved.id()).with(jwt().jwt(j -> j.claim("tenant_id", "tenant-b"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/snapshots/" + saved.beforeSnapshotId()).with(jwt().jwt(j -> j.claim("tenant_id", "tenant-b"))))
                .andExpect(status().isNotFound());
        String list = mvc.perform(get("/api/v1/analyses").param("service", service)
                .with(jwt().jwt(j -> j.claim("tenant_id", "tenant-b")))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals("[]", list);
        mvc.perform(get("/api/v1/analyses/" + saved.id()).with(jwt().jwt(j -> j.claim("tenant_id", "tenant-a"))))
                .andExpect(status().isOk());
        String owned = mvc.perform(get("/api/v1/analyses").param("service", service)
                .with(jwt().jwt(j -> j.claim("tenant_id", "tenant-a")))).andReturn().getResponse().getContentAsString();
        assertTrue(owned.contains(saved.id().toString()));
    }
    @Test void anonymousInvalidTokenAndMissingTenantDenied() throws Exception {
        mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/session").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/session").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
