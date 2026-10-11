package com.changeguard.security;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtValidationTest {
    static RSAPrivateKey privateKey;
    static NimbusJwtDecoder decoder;
    @BeforeAll static void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair(); privateKey = (RSAPrivateKey) keys.getPrivate();
        decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://issuer.example.test"),
                new RequiredJwtClaimsValidator("changeguard-api")));
    }
    String sign(String issuer, String audience, String tenant, Instant expires) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("user-123")
                .claim("tenant_id", tenant);
        if (expires != null) claims.expirationTime(Date.from(expires));
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(privateKey));
        return jwt.serialize();
    }
    @Test void signedValidTokenAccepted() throws Exception {
        assertEquals("tenant-a", decoder.decode(sign("https://issuer.example.test", "changeguard-api", "tenant-a",
                Instant.now().plusSeconds(600))).getClaimAsString("tenant_id"));
    }
    @Test void wrongIssuerAndAudienceRejected() throws Exception {
        String issuer = sign("https://other.example.test", "changeguard-api", "tenant-a", Instant.now().plusSeconds(600));
        String audience = sign("https://issuer.example.test", "other-api", "tenant-a", Instant.now().plusSeconds(600));
        assertThrows(JwtException.class, () -> decoder.decode(issuer));
        assertThrows(JwtException.class, () -> decoder.decode(audience));
    }
    @Test void expiredMissingExpiryAndInvalidTenantRejected() throws Exception {
        for (String token : new String[]{
                sign("https://issuer.example.test", "changeguard-api", "tenant-a", Instant.now().minusSeconds(300)),
                sign("https://issuer.example.test", "changeguard-api", "tenant-a", null),
                sign("https://issuer.example.test", "changeguard-api", "__legacy_unassigned__", Instant.now().plusSeconds(600)),
                sign("https://issuer.example.test", "changeguard-api", null, Instant.now().plusSeconds(600))})
            assertThrows(JwtException.class, () -> decoder.decode(token));
    }
    @Test void invalidSignatureRejected() throws Exception {
        String token = sign("https://issuer.example.test", "changeguard-api", "tenant-a", Instant.now().plusSeconds(600));
        String[] parts = token.split("\\.");
        String signature = parts[2];
        String tampered = parts[0] + "." + parts[1] + "." + (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
        assertThrows(JwtException.class, () -> decoder.decode(tampered));
    }
}
