package com.changeguard.integration.github;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.config.GitHubAppSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class GitHubAppJwtTest {
  @TempDir Path temp;
  final JsonMapper json = JsonMapper.builder().build();

  @Test
  void producesVerifiableRs256JwtWithBoundedClockClaims() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    Path key = temp.resolve("app.pem");
    Files.writeString(
        key,
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10})
                .encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n");
    var settings = new GitHubAppSettings(true, 42, "{}", true, json);
    Instant now = Instant.parse("2026-10-12T08:00:00Z");
    String token =
        new GitHubAppJwt(settings, key.toString(), json, Clock.fixed(now, ZoneOffset.UTC)).token();
    var parts = token.split("\\.");
    assertEquals(3, parts.length);
    var header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
    var payload = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
    assertEquals("RS256", header.path("alg").asText());
    assertEquals("42", payload.path("iss").asText());
    assertEquals(now.getEpochSecond() - 60, payload.path("iat").asLong());
    assertEquals(now.getEpochSecond() + 540, payload.path("exp").asLong());
    var verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(pair.getPublic());
    verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
    assertTrue(verifier.verify(Base64.getUrlDecoder().decode(parts[2])));
  }

  @Test
  void disabledModeNeedsNoKeyButCannotIssueJwt() {
    var settings = new GitHubAppSettings(false, 0, "invalid", false, json);
    assertThrows(IllegalStateException.class, () -> new GitHubAppJwt(settings, "", json).token());
  }

  @Test
  void missingAndWeakKeysFailClosed() throws Exception {
    var settings = new GitHubAppSettings(true, 42, "{}", true, json);
    assertThrows(
        IllegalArgumentException.class,
        () -> new GitHubAppJwt(settings, temp.resolve("missing.pem").toString(), json));
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(1024);
    var pair = generator.generateKeyPair();
    Path key = temp.resolve("weak.pem");
    Files.writeString(
        key,
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----");
    assertThrows(
        IllegalArgumentException.class, () -> new GitHubAppJwt(settings, key.toString(), json));
  }

  @Test
  void invalidOperatorApprovalsAndDisabledWebhookFailStartup() {
    assertThrows(
        IllegalArgumentException.class, () -> new GitHubAppSettings(true, 42, "{}", false, json));
    assertThrows(
        IllegalArgumentException.class, () -> new GitHubAppSettings(true, 0, "{}", true, json));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GitHubAppSettings(true, 42, "{\"not-a-uuid\":7}", true, json));
    assertThrows(
        IllegalArgumentException.class, () -> new GitHubAppSettings(true, 42, "null", true, json));
  }
}
