package com.changeguard.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import com.changeguard.integration.github.GitHubHttpTransport;
import com.changeguard.integration.github.GitHubTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GitHubConfig {
    @Bean GitHubTransport gitHubTransport() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new GitHubHttpTransport(client, URI.create("https://api.github.com"));
    }
}
