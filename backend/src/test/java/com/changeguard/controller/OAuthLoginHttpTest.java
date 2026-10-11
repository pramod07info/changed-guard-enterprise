package com.changeguard.controller;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuthLoginHttpTest {
    @LocalServerPort int port;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
    private final String verifier = "test-verifier-with-at-least-forty-three-characters-123456789";
    private final String redirect = "http://localhost:3000/oauth/callback";
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "Login must contain a CSRF token");
        return matcher.group(1);
    }
    private String authorizationPath() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        return "/oauth2/authorize?response_type=code&client_id=changeguard-dashboard&redirect_uri=" + encode(redirect)
                + "&scope=changeguard-api&state=test-state&code_challenge_method=S256&code_challenge=" + challenge;
    }
    private String loginAndCode() throws Exception {
        String path = authorizationPath();
        assertEquals(302, get(path).statusCode());
        var login = get("/login");
        assertEquals(200, login.statusCode());
        assertEquals(302, post("/login", "username=pramod&password=" + encode("ChangeGuard123!") + "&_csrf=" + encode(csrf(login.body()))).statusCode());
        var authorized = get(path);
        assertEquals(302, authorized.statusCode());
        String location = authorized.headers().firstValue("Location").orElseThrow();
        assertTrue(location.startsWith(redirect));
        assertTrue(location.contains("state=test-state"));
        var code = Pattern.compile("[?&]code=([^&]+)").matcher(location);
        assertTrue(code.find(), location);
        return code.group(1);
    }
    private String tokenBody(String code, String proof) {
        return "grant_type=authorization_code&client_id=changeguard-dashboard&redirect_uri=" + encode(redirect)
                + "&code=" + code + "&code_verifier=" + encode(proof);
    }
    @Test void databaseLoginPkceTokenAndProtectedSession() throws Exception {
        String code = loginAndCode();
        var response = post("/oauth2/token", tokenBody(code, verifier));
        assertEquals(200, response.statusCode(), response.body());
        String token = json.readTree(response.body()).get("access_token").asString();
        var session = client.send(HttpRequest.newBuilder(uri("/api/v1/session"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, session.statusCode(), session.body());
        assertTrue(session.body().contains("local-team"));
        assertTrue(session.body().contains("pramod"));
        assertEquals(400, post("/oauth2/token", tokenBody(code, verifier)).statusCode(), "Code must be single use");
        assertEquals(401, get("/api/v1/session").statusCode(), "Login cookie must not authenticate API requests");
    }
    @Test void incorrectPkceVerifierRejected() throws Exception {
        assertEquals(400, post("/oauth2/token", tokenBody(loginAndCode(), "wrong-proof-with-at-least-forty-three-characters-1234")).statusCode());
    }
    @Test void badPasswordAndMissingCsrfRejected() throws Exception {
        assertEquals(403, post("/login", "username=pramod&password=incorrect").statusCode());
        var login = get("/login");
        var rejected = post("/login", "username=pramod&password=incorrect&_csrf=" + encode(csrf(login.body())));
        assertEquals(302, rejected.statusCode());
        assertTrue(rejected.headers().firstValue("Location").orElseThrow().contains("error"));
    }
    @Test void missingPkceAndUnregisteredRedirectRejected() throws Exception {
        var missingProof = get("/oauth2/authorize?response_type=code&client_id=changeguard-dashboard&redirect_uri="
                + encode(redirect) + "&scope=changeguard-api&state=test-state");
        assertTrue(missingProof.statusCode() == 400 || missingProof.headers().firstValue("Location").orElse("").contains("error="));
        var wrongRedirect = get(authorizationPath().replace(encode(redirect), encode("http://attacker.invalid/callback")));
        assertEquals(400, wrongRedirect.statusCode());
        assertTrue(wrongRedirect.headers().firstValue("Location").isEmpty());
    }
    @Test void oldFixedTokenRejectedAndConfigPublic() throws Exception {
        assertEquals(200, get("/api/v1/auth/config").statusCode());
        var response = client.send(HttpRequest.newBuilder(uri("/api/v1/session"))
                .header("Authorization", "Bearer changeguard-local-development-token").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }
}
