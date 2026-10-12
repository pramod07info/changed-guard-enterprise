package com.changeguard.controller;

import java.net.URI;
import java.util.UUID;
import com.changeguard.dto.GitHubPrPreview;
import com.changeguard.dto.GitHubPrRequest;
import com.changeguard.dto.SavedAnalysis;
import com.changeguard.service.GitHubPrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/baselines/{id}/github-pr")
public class GitHubPrController {
    private final GitHubPrService github;
    public GitHubPrController(GitHubPrService github) { this.github = github; }
    @PostMapping("/preview")
    public GitHubPrPreview preview(@PathVariable UUID id, @RequestBody GitHubPrRequest request) { return github.preview(id, request); }
    @PostMapping("/analyses")
    public ResponseEntity<SavedAnalysis> analyze(@PathVariable UUID id, @RequestBody GitHubPrRequest request) {
        var result = github.analyzeAndSave(id, request);
        return ResponseEntity.created(URI.create("/api/v1/analyses/" + result.id())).body(result);
    }
}
