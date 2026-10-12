package com.changeguard.service;

import com.changeguard.config.FeatureFlags;
import com.changeguard.dto.ImpactReport.Consumer;
import com.changeguard.exception.InvalidContractException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Shared cycle-safe dependency traversal. Graph edges are consumer -> providers. */
@Service
public class DependencyGraphService {
  private final boolean enabled;

  public DependencyGraphService() {
    enabled = true;
  }

  @Autowired
  public DependencyGraphService(FeatureFlags flags) {
    enabled = flags.manifest().dependencyAnalysis();
  }

  public void validate(String service, Map<String, List<String>> dependencies) {
    if (blank(service)) throw new InvalidContractException("service must be a nonempty string");
    if (dependencies != null) {
      dependencies.forEach(
          (consumer, providers) -> {
            if (blank(consumer) || providers == null || providers.stream().anyMatch(this::blank)) {
              throw new InvalidContractException(
                  "dependencies must map consumer names to provider name lists");
            }
          });
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  public List<Consumer> trace(String service, Map<String, List<String>> dependencies) {
    validate(service, dependencies);
    if (!enabled) return List.of();
    var reverse = new HashMap<String, Set<String>>();
    if (dependencies != null) {
      dependencies.forEach(
          (consumer, providers) ->
              providers.forEach(
                  provider ->
                      reverse.computeIfAbsent(provider, ignored -> new TreeSet<>()).add(consumer)));
    }
    var seen = new HashSet<String>();
    seen.add(service);
    var queue = new ArrayDeque<List<String>>();
    queue.add(List.of(service));
    var result = new ArrayList<Consumer>();
    while (!queue.isEmpty()) {
      var path = queue.remove();
      for (String consumer : reverse.getOrDefault(path.getLast(), Set.of())) {
        if (seen.add(consumer)) {
          var nextPath = new ArrayList<>(path);
          nextPath.add(consumer);
          var immutablePath = List.copyOf(nextPath);
          result.add(new Consumer(consumer, immutablePath.size() - 1, immutablePath));
          queue.add(immutablePath);
        }
      }
    }
    return List.copyOf(result);
  }
}
