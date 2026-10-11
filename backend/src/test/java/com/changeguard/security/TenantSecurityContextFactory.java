package com.changeguard.security;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class TenantSecurityContextFactory implements WithSecurityContextFactory<WithTenant> {
    @Override public SecurityContext createSecurityContext(WithTenant annotation) {
        var jwt = Jwt.withTokenValue("fixture").header("alg", "RS256").subject("test-user")
                .claim("tenant_id", annotation.value()).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));
        return context;
    }
}
