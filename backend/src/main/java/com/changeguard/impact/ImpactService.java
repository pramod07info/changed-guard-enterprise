package com.changeguard.impact;

import java.util.*;
import org.springframework.stereotype.Service;
import com.changeguard.impact.ImpactReport.*;

@Service
public class ImpactService {
    private static final Set<String> TYPES = Set.of("string", "integer", "number", "boolean", "object", "array");

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
        var consumers = changed ? trace(request.service(), request.dependencies()) : List.<Consumer>of();
        var tests = consumers.stream().filter(c -> c.distance() == 1)
                .map(c -> "Run consumer contract tests: " + c.service() + " -> " + request.service()).toList();
        Risk risk = !findings.isEmpty() ? Risk.HIGH : changed ? Risk.LOW : Risk.NONE;
        return new ImpactReport(request.service(), risk, List.copyOf(findings), consumers, tests,
                !findings.isEmpty(), List.of(
                "Normalized response fields only; OpenAPI, request and nested schema analysis are not implemented.",
                "Service reachability indicates potential impact, not proven endpoint usage.",
                "Additive fields are low risk; strict clients may reject them."));
    }

    private List<Consumer> trace(String service, Map<String, List<String>> dependencies) {
        var reverse = new HashMap<String, Set<String>>();
        if (dependencies != null) {
            dependencies.forEach((consumer, providers) -> providers.forEach(provider ->
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

    private void validate(ImpactRequest request) {
        if (request == null || blank(request.service())) {
            throw new IllegalArgumentException("service must be a nonempty string");
        }
        validateContract(request.before(), "before");
        validateContract(request.after(), "after");
        if (request.dependencies() != null) {
            request.dependencies().forEach((consumer, providers) -> {
                if (blank(consumer) || providers == null || providers.stream().anyMatch(this::blank)) {
                    throw new IllegalArgumentException("dependencies must map consumer names to provider name lists");
                }
            });
        }
    }

    private void validateContract(Map<String, Map<String, String>> contract, String name) {
        if (contract == null) throw new IllegalArgumentException(name + " must be an endpoint map");
        contract.forEach((endpoint, fields) -> {
            if (blank(endpoint) || fields == null) {
                throw new IllegalArgumentException("Endpoints must map nonempty names to response field maps");
            }
            fields.forEach((field, type) -> {
                if (blank(field) || type == null || !TYPES.contains(type)) {
                    throw new IllegalArgumentException("Fields must use supported JSON type names");
                }
            });
        });
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
