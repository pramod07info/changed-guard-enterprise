package com.changeguard.service;

import com.changeguard.config.FeatureFlags;
import com.changeguard.dto.ImpactReport;
import com.changeguard.dto.ImpactReport.Risk;
import com.changeguard.dto.OpenApiReport;
import com.changeguard.dto.OpenApiReport.Change;
import com.changeguard.dto.OpenApiRequest;
import com.changeguard.exception.InvalidContractException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Bounded OpenAPI 3.0 JSON importer. Never fetches references or executes code. */
@Service
public class OpenApiService {
  private static final Set<String> METHODS =
      Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace");
  private static final Set<String> SCHEMA_KEYS =
      Set.of(
          "type",
          "properties",
          "required",
          "enum",
          "nullable",
          "items",
          "title",
          "description",
          "example",
          "deprecated");
  private final boolean recommendations;
  private final DependencyGraphService graph;

  public OpenApiService(DependencyGraphService graph) {
    this.graph = graph;
    this.recommendations = true;
  }

  record Schema(
      String type,
      boolean nullable,
      Set<Object> values,
      Map<String, Schema> properties,
      Set<String> required,
      Schema items) {}

  record Operation(Schema request, boolean requestRequired, Map<String, Schema> responses) {}

  @Autowired
  public OpenApiService(DependencyGraphService graph, FeatureFlags flags) {
    this.graph = graph;
    this.recommendations = flags.manifest().testRecommendations();
  }

  public OpenApiReport analyze(OpenApiRequest request) {
    if (request == null) throw invalid("request is required");
    // Reuse service-name and graph validation before importing either document.
    graph.validate(request.service(), request.dependencies());
    var before = operations(request.before());
    var after = operations(request.after());
    var changes = new ArrayList<Change>();
    for (String key : new TreeSet<>(before.keySet())) {
      Operation old = before.get(key), next = after.get(key);
      if (next == null) {
        add(changes, "OPERATION_REMOVED", key, "operation", "Existing callers lose this operation");
        continue;
      }
      if (!old.requestRequired() && next.requestRequired())
        add(
            changes,
            "REQUEST_BODY_REQUIRED",
            key,
            "request",
            "Previously optional request body is now required");
      if (old.request() == null && next.request() != null && next.requestRequired())
        add(
            changes,
            "REQUEST_BODY_ADDED",
            key,
            "request",
            "Existing callers do not send the new required body");
      if (old.request() != null && next.request() == null)
        add(
            changes,
            "REQUEST_BODY_REMOVED",
            key,
            "request",
            "Previously accepted request body is no longer described");
      if (old.request() != null && next.request() != null)
        compare(old.request(), next.request(), true, key, "request", changes);
      for (String status : new TreeSet<>(old.responses().keySet())) {
        if (!next.responses().containsKey(status))
          add(
              changes,
              "RESPONSE_STATUS_REMOVED",
              key,
              "response/" + status,
              "Previously documented response is removed");
        else {
          Schema a = old.responses().get(status), b = next.responses().get(status);
          if (a != null && b == null)
            add(
                changes,
                "RESPONSE_BODY_REMOVED",
                key,
                "response/" + status,
                "Response body is no longer described");
          else if (a != null) compare(a, b, false, key, "response/" + status, changes);
          else if (b != null)
            add(
                changes,
                "RESPONSE_BODY_ADDED",
                key,
                "response/" + status,
                "Previously bodyless response gains a body; consumer review required");
        }
      }
      for (String status : new TreeSet<>(next.responses().keySet()))
        if (!old.responses().containsKey(status))
          add(
              changes,
              "RESPONSE_STATUS_ADDED",
              key,
              "response/" + status,
              "Consumers may not handle this new response status");
    }
    boolean changed = !before.equals(after);
    var consumers =
        changed
            ? graph.trace(request.service(), request.dependencies())
            : List.<ImpactReport.Consumer>of();
    var tests =
        consumers.stream()
            .filter(c -> c.distance() == 1)
            .map(c -> "Run consumer contract tests: " + c.service() + " -> " + request.service())
            .toList();
    return new OpenApiReport(
        request.service(),
        changes.isEmpty() ? (changed ? Risk.LOW : Risk.NONE) : Risk.HIGH,
        List.copyOf(changes),
        consumers,
        recommendations ? tests : List.of(),
        !changes.isEmpty(),
        List.of(
            "Bounded OpenAPI 3.0 JSON subset, not a complete OpenAPI validator or compatibility"
                + " proof.",
            "Only application/json bodies and supported schema keywords are analyzed.",
            "Parameters, security, headers, composition, constraints and recursive/external"
                + " references are rejected.",
            "Removed optional response fields and new response statuses conservatively require"
                + " review.",
            "Potential consumers are service-level reachability, not verified endpoint usage."));
  }

  private void compare(
      Schema old,
      Schema next,
      boolean request,
      String operation,
      String location,
      List<Change> changes) {
    String prefix = request ? "REQUEST_" : "RESPONSE_";
    if (!old.type().equals(next.type())) {
      add(
          changes,
          prefix + "TYPE_CHANGED",
          operation,
          location,
          old.type() + " changed to " + next.type());
      return;
    }
    if (request ? old.nullable() && !next.nullable() : !old.nullable() && next.nullable())
      add(
          changes,
          prefix + "NULLABILITY_CHANGED",
          operation,
          location,
          request ? "Null is no longer accepted" : "Response can now be null");
    boolean enumBreak =
        request
            ? next.values() != null
                && (old.values() == null || !next.values().containsAll(old.values()))
            : old.values() != null
                && (next.values() == null || !old.values().containsAll(next.values()));
    if (enumBreak)
      add(
          changes,
          prefix + "ENUM_CHANGED",
          operation,
          location,
          request ? "Accepted values are narrowed" : "Possible response values are widened");
    if ("object".equals(old.type())) {
      var requiredBreak = new TreeSet<>(request ? next.required() : old.required());
      requiredBreak.removeAll(request ? old.required() : next.required());
      for (String field : requiredBreak)
        add(
            changes,
            prefix + "REQUIRED_CHANGED",
            operation,
            location + "/" + escape(field),
            request
                ? "New required input field"
                : "Previously guaranteed response field is now optional");
      for (String field : new TreeSet<>(old.properties().keySet())) {
        Schema b = next.properties().get(field);
        if (b == null) {
          if (!request)
            add(
                changes,
                "RESPONSE_FIELD_REMOVED",
                operation,
                location + "/" + escape(field),
                "Previously documented response field is removed");
          // OpenAPI default additionalProperties allows unlisted request fields.
        } else
          compare(
              old.properties().get(field),
              b,
              request,
              operation,
              location + "/" + escape(field),
              changes);
      }
    } else if ("array".equals(old.type()))
      compare(old.items(), next.items(), request, operation, location + "/items", changes);
  }

  private Map<String, Operation> operations(Map<String, Object> document) {
    if (document == null
        || !(document.get("openapi") instanceof String version)
        || !version.matches("3\\.0\\.[0-9]+"))
      throw invalid("Only OpenAPI 3.0.x JSON documents are supported");
    checkBudget(document, 0, new int[] {0});
    if (document.containsKey("security"))
      throw invalid("Global security analysis is not supported");
    var paths = object(document.get("paths"), "paths");
    if (paths.size() > 500) throw invalid("At most 500 paths are supported");
    var result = new TreeMap<String, Operation>();
    paths.forEach(
        (path, rawPath) -> {
          if (!path.startsWith("/")) throw invalid("Path must start with /");
          var item = object(rawPath, "path item");
          for (String field : item.keySet())
            if (!METHODS.contains(field) && !Set.of("summary", "description").contains(field))
              throw invalid("Unsupported path item keyword: " + field);
          item.forEach(
              (method, rawOperation) -> {
                if (!METHODS.contains(method)) return;
                var op = object(rawOperation, "operation");
                for (String field : op.keySet())
                  if (!Set.of(
                          "requestBody",
                          "responses",
                          "summary",
                          "description",
                          "operationId",
                          "tags",
                          "deprecated")
                      .contains(field)) throw invalid("Unsupported operation keyword: " + field);
                Schema body = null;
                boolean required = false;
                if (op.containsKey("requestBody")) {
                  var requestBody = object(op.get("requestBody"), "requestBody");
                  only(requestBody, Set.of("description", "required", "content"), "requestBody");
                  required = bool(requestBody, "required");
                  body = content(requestBody, document);
                  if (body == null)
                    throw invalid("requestBody must describe application/json content");
                }
                var responses = object(op.get("responses"), "responses");
                if (responses.isEmpty()) throw invalid("An operation needs responses");
                var schemas = new TreeMap<String, Schema>();
                responses.forEach(
                    (status, rawResponse) -> {
                      if (!status.matches("[1-5][0-9]{2}|default"))
                        throw invalid("Unsupported response status: " + status);
                      var response = object(rawResponse, "response");
                      only(response, Set.of("description", "content"), "response");
                      schemas.put(status, content(response, document));
                    });
                result.put(
                    method.toUpperCase(Locale.ROOT) + " " + path,
                    new Operation(body, required, Collections.unmodifiableMap(schemas)));
              });
        });
    return Collections.unmodifiableMap(result);
  }

  private Schema content(Map<String, Object> holder, Map<String, Object> document) {
    if (!holder.containsKey("content")) return null;
    var content = object(holder.get("content"), "content");
    if (!content.keySet().equals(Set.of("application/json")))
      throw invalid("Only application/json content is supported");
    var media = object(content.get("application/json"), "media");
    only(media, Set.of("schema", "example", "examples"), "media");
    return schema(object(media.get("schema"), "schema"), document, new HashSet<>(), 0);
  }

  private Schema schema(
      Map<String, Object> node, Map<String, Object> document, Set<String> refs, int depth) {
    if (depth > 32) throw invalid("Schema nesting exceeds 32 levels");
    if (node.containsKey("$ref")) {
      if (node.size() != 1
          || !(node.get("$ref") instanceof String ref)
          || !ref.startsWith("#/components/schemas/"))
        throw invalid("Only standalone local components/schemas references are supported");
      if (!refs.add(ref)) throw invalid("Recursive schema reference: " + ref);
      Object target = document;
      for (String token : ref.substring(2).split("/", -1))
        target = object(target, "reference").get(token.replace("~1", "/").replace("~0", "~"));
      Schema resolved = schema(object(target, "reference target"), document, refs, depth + 1);
      refs.remove(ref);
      return resolved;
    }
    only(node, SCHEMA_KEYS, "schema");
    Object type = node.get("type");
    if (!(type instanceof String name)
        || !Set.of("string", "integer", "number", "boolean", "object", "array").contains(name))
      throw invalid("Every schema needs a supported explicit type");
    boolean nullable = bool(node, "nullable");
    Set<Object> values = null;
    if (node.containsKey("enum")) {
      if (!(node.get("enum") instanceof List<?> list) || list.isEmpty())
        throw invalid("enum must be a nonempty list");
      for (Object value : list) {
        boolean matches =
            value == null
                ? nullable
                : switch (name) {
                  case "string" -> value instanceof String;
                  case "boolean" -> value instanceof Boolean;
                  case "integer" ->
                      value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue());
                  case "number" -> value instanceof Number;
                  default -> false;
                };
        if (!matches) throw invalid("Only matching primitive enum values are supported");
      }
      values = Collections.unmodifiableSet(new HashSet<>(list));
    }
    var properties = new TreeMap<String, Schema>();
    var required = new TreeSet<String>();
    Schema items = null;
    if ("object".equals(name)) {
      if (node.containsKey("properties"))
        object(node.get("properties"), "properties")
            .forEach(
                (key, value) ->
                    properties.put(
                        key, schema(object(value, "property"), document, refs, depth + 1)));
      if (node.containsKey("required")) {
        if (!(node.get("required") instanceof List<?> list))
          throw invalid("required must be a list");
        for (Object value : list) {
          if (!(value instanceof String field) || !properties.containsKey(field))
            throw invalid("Required fields must name declared properties");
          required.add(field);
        }
      }
      if (node.containsKey("items")) throw invalid("items is only supported for arrays");
    } else if ("array".equals(name)) {
      items = schema(object(node.get("items"), "array items"), document, refs, depth + 1);
      if (node.containsKey("properties") || node.containsKey("required"))
        throw invalid("Object keywords on array");
    } else if (node.containsKey("properties")
        || node.containsKey("required")
        || node.containsKey("items"))
      throw invalid("Structural keywords do not match primitive type");
    return new Schema(
        name,
        nullable,
        values,
        Collections.unmodifiableMap(properties),
        Collections.unmodifiableSet(required),
        items);
  }

  private void checkBudget(Object value, int depth, int[] count) {
    if (depth > 64 || ++count[0] > 50000) throw invalid("Document exceeds structural limits");
    if (value instanceof Map<?, ?> map) map.values().forEach(v -> checkBudget(v, depth + 1, count));
    else if (value instanceof List<?> list) list.forEach(v -> checkBudget(v, depth + 1, count));
  }

  private boolean bool(Map<String, Object> node, String key) {
    if (!node.containsKey(key)) return false;
    if (!(node.get(key) instanceof Boolean value)) throw invalid(key + " must be a boolean");
    return value;
  }

  private void only(Map<String, Object> node, Set<String> allowed, String location) {
    for (String key : node.keySet())
      if (!allowed.contains(key)) throw invalid("Unsupported " + location + " keyword: " + key);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> object(Object value, String location) {
    if (!(value instanceof Map<?, ?> map)
        || map.keySet().stream().anyMatch(k -> !(k instanceof String)))
      throw invalid(location + " must be an object");
    return (Map<String, Object>) map;
  }

  private InvalidContractException invalid(String message) {
    return new InvalidContractException(message);
  }

  private String escape(String value) {
    return value.replace("~", "~0").replace("/", "~1");
  }

  private void add(
      List<Change> changes, String code, String op, String location, String explanation) {
    changes.add(new Change(code, op, location, explanation));
  }
}
