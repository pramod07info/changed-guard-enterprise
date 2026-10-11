package com.changeguard.impact;

import org.springframework.web.bind.annotation.*;

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
