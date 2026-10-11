package com.changeguard.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.changeguard.dto.AnalysisSummary;
import com.changeguard.dto.OpenApiRequest;
import com.changeguard.dto.SavedAnalysis;
import com.changeguard.dto.SnapshotResponse;
import com.changeguard.service.AnalysisHistoryService;

@RestController
@RequestMapping("/api/v1")
public class AnalysisHistoryController {
    private final AnalysisHistoryService history;
    public AnalysisHistoryController(AnalysisHistoryService history) { this.history = history; }
    @PostMapping("/analyses")
    public ResponseEntity<SavedAnalysis> create(@RequestBody OpenApiRequest request) {
        var saved = history.create(request);
        return ResponseEntity.created(URI.create("/api/v1/analyses/" + saved.id())).body(saved);
    }
    @GetMapping("/analyses/{id}")
    public SavedAnalysis get(@PathVariable UUID id) { return history.get(id); }
    @GetMapping("/analyses")
    public List<AnalysisSummary> list(@RequestParam String service,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        return history.list(service, limit, offset);
    }
    @GetMapping("/snapshots/{id}")
    public SnapshotResponse snapshot(@PathVariable UUID id) { return history.snapshot(id); }
}
