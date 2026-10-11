package com.changeguard.controller;

import com.changeguard.dto.ImpactReport;
import com.changeguard.dto.ImpactRequest;
import com.changeguard.service.ImpactService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/impact")
public class ImpactController {
    private final ImpactService service;
    public ImpactController(ImpactService service) { this.service = service; }

    @PostMapping
    public ImpactReport analyze(@RequestBody ImpactRequest request) {
        return service.analyze(request);
    }
}
