package com.changeguard.controller;

import com.changeguard.dto.AuthConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthConfigurationController {
    private final String issuer;
    public AuthConfigurationController(@Value("${changeguard.auth.issuer}") String issuer) {
        this.issuer = issuer.replaceAll("/+$", "");
    }
    @GetMapping("/config")
    public AuthConfiguration configuration() {
        return new AuthConfiguration(issuer + "/oauth2/authorize", issuer + "/oauth2/token", issuer + "/logout", "changeguard-dashboard");
    }
}
