package com.changeguard.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class RequiredJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;
    public RequiredJwtClaimsValidator(String audience) { this.audience = audience; }
    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object subject = jwt.getClaims().get("sub");
        if (jwt.getExpiresAt() != null && subject instanceof String sub && !sub.isBlank()
                && jwt.getAudience().contains(audience)
                && TenantContext.validTenant(jwt.getClaims().get("tenant_id")))
            return OAuth2TokenValidatorResult.success();
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required identity, tenant, expiration or audience is missing", null));
    }
}
