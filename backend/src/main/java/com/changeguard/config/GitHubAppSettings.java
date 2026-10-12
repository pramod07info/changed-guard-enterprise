package com.changeguard.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Operator approvals, never credentials or installation IDs supplied by tenant requests. */
@Component
public class GitHubAppSettings {
  private final boolean enabled;
  private final long appId;
  private final Map<UUID, Long> bindings;

  @org.springframework.beans.factory.annotation.Autowired
  public GitHubAppSettings(
      FeatureFlags flags,
      @Value("${changeguard.github.app.id:0}") long appId,
      @Value("${changeguard.github.app.bindings:{}}") String bindingsJson,
      JsonMapper json) {
    this(flags.manifest().githubChecks(), appId, bindingsJson, flags.manifest().webhooks(), json);
  }

  public GitHubAppSettings(
      @Value("${changeguard.github.app.enabled:false}") boolean enabled,
      @Value("${changeguard.github.app.id:0}") long appId,
      @Value("${changeguard.github.app.bindings:{}}") String bindingsJson,
      @Value("${changeguard.github.webhooks.enabled:false}") boolean webhooksEnabled,
      JsonMapper json) {
    this.enabled = enabled;
    this.appId = appId;
    if (enabled && (!webhooksEnabled || appId < 1))
      throw new IllegalArgumentException("Checks require webhooks and a positive GitHub App ID");
    var values = new HashMap<UUID, Long>();
    if (enabled) {
      try {
        Map<String, Long> input =
            json.readerFor(new TypeReference<Map<String, Long>>() {})
                .with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readValue(bindingsJson);
        if (input == null || input.size() > 100) throw new IllegalArgumentException();
        input.forEach(
            (id, installation) -> {
              if (installation == null
                  || installation < 1
                  || !UUID.fromString(id).toString().equals(id))
                throw new IllegalArgumentException();
              values.put(UUID.fromString(id), installation);
            });
      } catch (RuntimeException e) {
        throw new IllegalArgumentException(
            "App bindings must map at most 100 canonical subscription UUIDs to positive"
                + " installation IDs");
      }
    }
    this.bindings = Map.copyOf(values);
  }

  public boolean enabled() {
    return enabled;
  }

  public long appId() {
    return appId;
  }

  public Map<UUID, Long> bindings() {
    return bindings;
  }

  public boolean approved(UUID subscription, long installation) {
    return enabled && Long.valueOf(installation).equals(bindings.get(subscription));
  }

  public static String checkName(UUID subscription) {
    return "ChangeGuard / " + subscription;
  }
}
