package com.changeguard.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** HTTP fixtures only. Production signature and claim checks have separate tests. */
@TestConfiguration
public class TestJwtConfiguration {
    @Bean @Primary JwtDecoder fixtureDecoder() {
        return token -> {
            if (!token.equals("test-token")) throw new BadJwtException("Invalid fixture token");
            return Jwt.withTokenValue(token).header("alg", "RS256").subject("test-user")
                    .claim("tenant_id", "tenant-a").build();
        };
    }
}
