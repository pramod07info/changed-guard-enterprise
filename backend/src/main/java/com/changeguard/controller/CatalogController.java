package com.changeguard.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import com.changeguard.dto.*;
import com.changeguard.service.CatalogService;
import com.changeguard.service.BaselineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;
    private final BaselineService baselines;
    public CatalogController(CatalogService catalog, BaselineService baselines) { this.catalog = catalog; this.baselines = baselines; }
    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> createProject(@RequestBody ProjectRequest request) {
        var result = catalog.createProject(request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + result.id())).body(result);
    }
    @GetMapping("/projects")
    public List<ProjectResponse> projects(@RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        return catalog.projects(limit, offset);
    }
    @GetMapping("/projects/{id}")
    public ProjectResponse project(@PathVariable UUID id) { return catalog.project(id); }
    @PostMapping("/projects/{id}/services")
    public ResponseEntity<CatalogServiceResponse> createService(@PathVariable UUID id, @RequestBody CatalogServiceRequest request) {
        var result = catalog.createService(id, request);
        return ResponseEntity.created(URI.create("/api/v1/services/" + result.id())).body(result);
    }
    @GetMapping("/projects/{id}/services")
    public List<CatalogServiceResponse> services(@PathVariable UUID id, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) { return catalog.services(id, limit, offset); }
    @GetMapping("/services/{id}")
    public CatalogServiceResponse service(@PathVariable UUID id) { return catalog.service(id); }
    @PostMapping("/services/{id}/baselines")
    public ResponseEntity<BaselineResponse> publish(@PathVariable UUID id, @RequestBody BaselineRequest request) {
        var result = baselines.publish(id, request);
        return ResponseEntity.created(URI.create("/api/v1/baselines/" + result.id())).body(result);
    }
    @GetMapping("/services/{id}/baselines")
    public List<BaselineSummary> baselines(@PathVariable UUID id, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) { return baselines.list(id, limit, offset); }
    @GetMapping("/baselines/{id}")
    public BaselineResponse baseline(@PathVariable UUID id) { return baselines.get(id); }
    @PostMapping("/baselines/{id}/preview")
    public OpenApiReport preview(@PathVariable UUID id, @RequestBody BaselineAnalysisRequest request) { return baselines.preview(id, request); }
    @PostMapping("/baselines/{id}/analyses")
    public ResponseEntity<SavedAnalysis> analyze(@PathVariable UUID id, @RequestBody BaselineAnalysisRequest request) {
        var result = baselines.analyzeAndSave(id, request);
        return ResponseEntity.created(URI.create("/api/v1/analyses/" + result.id())).body(result);
    }
}
