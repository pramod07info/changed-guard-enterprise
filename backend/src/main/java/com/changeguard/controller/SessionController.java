package com.changeguard.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.changeguard.dto.SessionResponse;
import com.changeguard.security.TenantContext;

@RestController
public class SessionController {
    private final TenantContext tenant;
    public SessionController(TenantContext tenant) { this.tenant = tenant; }
    @GetMapping("/api/v1/session")
    public SessionResponse session(Authentication auth) {
        return new SessionResponse(auth.getName(), tenant.requiredTenant());
    }
}
