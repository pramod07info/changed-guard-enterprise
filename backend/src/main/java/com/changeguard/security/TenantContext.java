package com.changeguard.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {
    public static boolean validTenant(Object value) {
        return value instanceof String tenant && tenant.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    }
    public String requiredTenant() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)
                || !validTenant(jwt.getClaims().get("tenant_id")))
            throw new AccessDeniedException("A verified tenant identity is required");
        return (String) jwt.getClaims().get("tenant_id");
    }
}
