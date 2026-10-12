package com.changeguard.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GitHubWebhookSignature {
  private final String secret;
  private final boolean enabled;

  @org.springframework.beans.factory.annotation.Autowired
  public GitHubWebhookSignature(
      com.changeguard.config.FeatureFlags flags,
      @Value("${changeguard.github.webhooks.secret:}") String secret) {
    this(secret, flags.manifest().webhooks());
  }

  public GitHubWebhookSignature(
      @Value("${changeguard.github.webhooks.secret:}") String secret,
      @Value("${changeguard.github.webhooks.enabled:false}") boolean enabled) {
    this.secret = secret;
    this.enabled = enabled;
    if (enabled && secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("Webhook secret must contain at least 32 UTF-8 bytes");
  }

  public void verify(byte[] body, String signature) {
    requireEnabled();
    if (signature == null || !signature.matches("sha256=[a-fA-F0-9]{64}")) throw denied();
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      if (!MessageDigest.isEqual(
          mac.doFinal(body), HexFormat.of().parseHex(signature.substring(7)))) throw denied();
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("HMAC unavailable", e);
    }
  }

  public void requireEnabled() {
    if (!enabled)
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE, "GitHub webhooks are disabled");
  }

  private ResponseStatusException denied() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
  }
}
