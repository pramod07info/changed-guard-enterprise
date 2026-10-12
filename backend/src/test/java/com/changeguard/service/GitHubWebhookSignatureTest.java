package com.changeguard.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

public class GitHubWebhookSignatureTest {
  static final String SECRET = "webhook-test-only-secret-32-characters";

  public static String sign(byte[] body) throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
  }

  @Test
  void acceptsExactUtf8BytesAndRejectsChanges() throws Exception {
    byte[] body = "{\"name\":\"café\"}".getBytes(StandardCharsets.UTF_8);
    var verifier = new GitHubWebhookSignature(SECRET, true);
    verifier.verify(body, sign(body));
    assertThrows(ResponseStatusException.class, () -> verifier.verify("{}".getBytes(), sign(body)));
  }

  @Test
  void rejectsMissingMalformedAndWrongSignatures() {
    var verifier = new GitHubWebhookSignature(SECRET, true);
    for (String signature :
        new String[] {null, "sha1=" + "a".repeat(40), "sha256=nope", "sha256=" + "0".repeat(64)})
      assertEquals(
          401,
          assertThrows(ResponseStatusException.class, () -> verifier.verify(new byte[0], signature))
              .getStatusCode()
              .value());
  }

  @Test
  void disabledAndWeakConfigurationsFailClosed() {
    assertThrows(IllegalArgumentException.class, () -> new GitHubWebhookSignature("short", true));
    assertEquals(
        503,
        assertThrows(
                ResponseStatusException.class,
                () -> new GitHubWebhookSignature("", false).verify(new byte[0], null))
            .getStatusCode()
            .value());
  }
}
