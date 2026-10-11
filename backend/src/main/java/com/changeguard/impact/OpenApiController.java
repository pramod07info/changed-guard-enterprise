package com.changeguard.impact;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/impact/openapi")
public class OpenApiController {
    private final OpenApiService service;
    public OpenApiController(OpenApiService service) { this.service = service; }
    @PostMapping
    public OpenApiReport analyze(@RequestBody OpenApiRequest request) {
        return service.analyze(request);
    }
}
