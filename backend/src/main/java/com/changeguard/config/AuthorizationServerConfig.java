package com.changeguard.config;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import com.changeguard.repository.UserRepository;
import com.changeguard.security.RequiredJwtClaimsValidator;
import com.changeguard.security.TenantContext;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
public class AuthorizationServerConfig {
    @Bean @Order(1)
    SecurityFilterChain authorizationSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/oauth2/**", "/.well-known/oauth-authorization-server")
                .cors(Customizer.withDefaults()).oauth2AuthorizationServer(Customizer.withDefaults())
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .build();
    }

    @Bean @Order(3)
    SecurityFilterChain loginSecurity(HttpSecurity http,
            @Value("${changeguard.auth.frontend-url}") String frontend) throws Exception {
        return http.authorizeHttpRequests(requests -> requests.requestMatchers("/login", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .logout(logout -> logout.logoutSuccessUrl(frontend).invalidateHttpSession(true).deleteCookies("JSESSIONID"))
                .build();
    }

    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }

    @Bean RegisteredClientRepository registeredClients(@Value("${changeguard.auth.redirect-uris}") String redirects) {
        var client = RegisteredClient.withId("changeguard-dashboard")
                .clientId("changeguard-dashboard")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("changeguard-api")
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(30)).build());
        Arrays.stream(redirects.split(",")).map(String::trim).forEach(client::redirectUri);
        return new InMemoryRegisteredClientRepository(client.build());
    }

    @Bean OAuth2AuthorizationService authorizations() { return new InMemoryOAuth2AuthorizationService(); }

    @Bean AuthorizationServerSettings authorizationSettings(@Value("${changeguard.auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean RSAKey signingKey(Environment env, @Value("${changeguard.auth.signing-jwk:}") String configured) throws Exception {
        var profiles = Arrays.asList(env.getActiveProfiles());
        if (profiles.contains("local") && !profiles.equals(List.of("local"))) {
            throw new IllegalStateException("The demo local profile cannot be combined with other profiles");
        }
        if (!configured.isBlank()) {
            var key = RSAKey.parse(configured);
            if (!key.isPrivate() || key.size() < 2048 || key.getKeyID() == null) {
                throw new IllegalStateException("Signing JWK requires a private RSA key of at least 2048 bits and a kid");
            }
            return key;
        }
        if (!Arrays.asList(env.getActiveProfiles()).equals(List.of("local"))) {
            throw new IllegalStateException("Set CHANGEGUARD_SIGNING_JWK outside the exclusive local profile");
        }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString()).build();
    }

    @Bean JWKSource<SecurityContext> jwkSource(RSAKey key) {
        var keys = new JWKSet(key);
        return (selector, context) -> selector.select(keys);
    }

    @Bean JwtDecoder jwtDecoder(RSAKey key, @Value("${changeguard.auth.issuer}") String issuer) throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new RequiredJwtClaimsValidator("changeguard-api")));
        return decoder;
    }

    @Bean OAuth2TokenCustomizer<JwtEncodingContext> tokenClaims(UserRepository users) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                var user = users.findByUsername(context.getPrincipal().getName())
                        .filter(u -> u.enabled() && TenantContext.validTenant(u.tenantId()))
                        .orElseThrow(() -> new IllegalStateException("No enabled tenant membership"));
                context.getClaims().audience(List.of("changeguard-api")).claim("tenant_id", user.tenantId());
            }
        };
    }
}
