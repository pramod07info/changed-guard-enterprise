package com.changeguard.service;

import com.changeguard.dto.ImpactReport;
import com.changeguard.dto.ImpactReport.Consumer;
import com.changeguard.dto.ImpactReport.Finding;
import com.changeguard.dto.ImpactReport.Risk;
import com.changeguard.dto.ImpactRequest;
import com.changeguard.exception.InvalidContractException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

@Service
public class ImpactService {
  private final boolean recommendations;
  private final DependencyGraphService graph;

  public ImpactService(DependencyGraphService graph) {
    this.graph = graph;
    this.recommendations = true;
  }

  private static final Set<String> TYPES =
      Set.of("string", "integer", "number", "boolean", "object", "array");

  @org.springframework.beans.factory.annotation.Autowired
  public ImpactService(DependencyGraphService graph, com.changeguard.config.FeatureFlags flags) {
    this.graph = graph;
    this.recommendations = flags.manifest().testRecommendations();
  }

  public ImpactReport analyze(ImpactRequest request) {
    validate(request);
    var findings = new ArrayList<Finding>();
    for (String endpoint : new TreeSet<>(request.before().keySet())) {
      var previous = request.before().get(endpoint);
      var next = request.after().get(endpoint);
      if (next == null) {
        findings.add(new Finding("ENDPOINT_REMOVED", endpoint, null, Risk.HIGH));
        continue;
      }
      for (String field : new TreeSet<>(previous.keySet())) {
        if (!next.containsKey(field)) {
          findings.add(new Finding("RESPONSE_FIELD_REMOVED", endpoint, field, Risk.HIGH));
        } else if (!previous.get(field).equals(next.get(field))) {
          findings.add(new Finding("RESPONSE_TYPE_CHANGED", endpoint, field, Risk.HIGH));
        }
      }
    }
    boolean changed = !request.before().equals(request.after());
    var consumers =
        changed ? graph.trace(request.service(), request.dependencies()) : List.<Consumer>of();
    var tests =
        consumers.stream()
            .filter(c -> c.distance() == 1)
            .map(c -> "Run consumer contract tests: " + c.service() + " -> " + request.service())
            .toList();
    Risk risk = !findings.isEmpty() ? Risk.HIGH : changed ? Risk.LOW : Risk.NONE;
    return new ImpactReport(
        request.service(),
        risk,
        List.copyOf(findings),
        consumers,
        recommendations ? tests : List.of(),
        !findings.isEmpty(),
        List.of(
            "Normalized response fields only; OpenAPI, request and nested schema analysis are not"
                + " implemented.",
            "Service reachability indicates potential impact, not proven endpoint usage.",
            "Additive fields are low risk; strict clients may reject them."));
  }

  private void validate(ImpactRequest request) {
    if (request == null || blank(request.service())) {
      throw new InvalidContractException("service must be a nonempty string");
    }
    validateContract(request.before(), "before");
    validateContract(request.after(), "after");
    graph.validate(request.service(), request.dependencies());
  }

  private void validateContract(Map<String, Map<String, String>> contract, String name) {
    if (contract == null) throw new InvalidContractException(name + " must be an endpoint map");
    contract.forEach(
        (endpoint, fields) -> {
          if (blank(endpoint) || fields == null) {
            throw new InvalidContractException(
                "Endpoints must map nonempty names to response field maps");
          }
          fields.forEach(
              (field, type) -> {
                if (blank(field) || type == null || !TYPES.contains(type)) {
                  throw new InvalidContractException("Fields must use supported JSON type names");
                }
              });
        });
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
