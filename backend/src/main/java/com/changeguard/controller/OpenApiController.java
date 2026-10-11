package com.changeguard.controller;

import com.changeguard.dto.OpenApiReport;
import com.changeguard.dto.OpenApiRequest;
import com.changeguard.service.OpenApiService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
