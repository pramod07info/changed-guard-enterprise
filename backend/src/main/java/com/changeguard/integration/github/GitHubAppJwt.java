package com.changeguard.integration.github;

import com.changeguard.config.GitHubAppSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GitHubAppJwt {
  private final GitHubAppSettings settings;
  private final PrivateKey key;
  private final JsonMapper json;
  private final Clock clock;

  @Autowired
  public GitHubAppJwt(
      GitHubAppSettings settings,
      @Value("${changeguard.github.app.private-key-file:}") String file,
      JsonMapper json) {
    this(settings, file, json, Clock.systemUTC());
  }

  GitHubAppJwt(GitHubAppSettings settings, String file, JsonMapper json, Clock clock) {
    this.settings = settings;
    this.json = json;
    this.clock = clock;
    PrivateKey loaded = null;
    if (settings.enabled()) {
      try {
        Path path = Path.of(file);
        if (!Files.isRegularFile(path) || Files.size(path) > 16384)
          throw new IllegalArgumentException();
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        if (!pem.startsWith("-----BEGIN PRIVATE KEY-----")
            || !pem.trim().endsWith("-----END PRIVATE KEY-----"))
          throw new IllegalArgumentException();
        byte[] der =
            Base64.getDecoder()
                .decode(
                    pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", ""));
        loaded = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        if (!(loaded instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < 2048)
          throw new IllegalArgumentException();
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "App checks require a readable PKCS#8 RSA PEM key of at least 2048 bits");
      }
    }
    this.key = loaded;
  }

  public String token() {
    if (!settings.enabled() || key == null)
      throw new IllegalStateException("GitHub App checks disabled");
    long now = clock.instant().getEpochSecond();
    String input =
        b64(json.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")))
            + "."
            + b64(
                json.writeValueAsBytes(
                    Map.of(
                        "iss",
                        String.valueOf(settings.appId()),
                        "iat",
                        now - 60,
                        "exp",
                        now + 540)));
    try {
      var signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(key);
      signature.update(input.getBytes(StandardCharsets.US_ASCII));
      return input + "." + b64(signature.sign());
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Unable to sign GitHub App JWT");
    }
  }

  private String b64(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
