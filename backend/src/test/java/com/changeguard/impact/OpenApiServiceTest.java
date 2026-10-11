package com.changeguard.impact;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.changeguard.impact.ImpactReport.Risk;

class OpenApiServiceTest {
    private final OpenApiService service = new OpenApiService(new ImpactService());
    private Map<String, Object> primitive(String type) { return Map.of("type", type); }
    private Map<String, Object> object(Map<String, Object> fields, List<String> required) {
        return Map.of("type", "object", "properties", fields, "required", required);
    }
    private Map<String, Object> content(Map<String, Object> schema) {
        return Map.of("content", Map.of("application/json", Map.of("schema", schema)));
    }
    private Map<String, Object> doc(Map<String, Object> request, Map<String, Object> response) {
        var operation = new HashMap<String, Object>();
        operation.put("responses", Map.of("200", content(response)));
        if (request != null) operation.put("requestBody", content(request));
        return Map.of("openapi", "3.0.3", "paths", Map.of("/orders", Map.of("post", operation)));
    }
    private OpenApiReport analyze(Map<String, Object> a, Map<String, Object> b) {
        return service.analyze(new OpenApiRequest("orders", a, b,
                Map.of("checkout", List.of("orders"), "storefront", List.of("checkout"), "orders", List.of("storefront"))));
    }
    private boolean has(OpenApiReport report, String code) {
        return report.breakingChanges().stream().anyMatch(c -> c.code().equals(code));
    }
    @Test void unchangedAndMetadataOnly() {
        var a = doc(null, primitive("string"));
        assertEquals(Risk.NONE, analyze(a, a).risk());
        var b = new HashMap<>(a); b.put("info", Map.of("title", "Updated title"));
        assertEquals(Risk.NONE, analyze(a, b).risk());
    }
    @Test void operationRemovalAndTransitiveCycle() {
        var report = analyze(doc(null, primitive("string")), Map.of("openapi", "3.0.3", "paths", Map.of()));
        assertTrue(has(report, "OPERATION_REMOVED"));
        assertEquals(List.of("checkout", "storefront"), report.potentialConsumers().stream().map(ImpactReport.Consumer::service).toList());
        assertEquals(List.of("orders", "checkout", "storefront"), report.potentialConsumers().getLast().path());
    }
    @Test void newRequiredRequestField() {
        var fields = Map.<String, Object>of("id", primitive("string"));
        var report = analyze(doc(object(fields, List.of()), primitive("string")),
                doc(object(fields, List.of("id")), primitive("string")));
        assertTrue(has(report, "REQUEST_REQUIRED_CHANGED"));
        assertEquals("request/id", report.breakingChanges().getFirst().location());
    }
    @Test void requestRequiredFieldRelaxationIsLow() {
        var fields = Map.<String, Object>of("id", primitive("string"));
        assertEquals(Risk.LOW, analyze(doc(object(fields, List.of("id")), primitive("string")),
                doc(object(fields, List.of()), primitive("string"))).risk());
    }
    @Test void responseGuaranteeRelaxationIsBreaking() {
        var fields = Map.<String, Object>of("id", primitive("string"));
        assertTrue(has(analyze(doc(null, object(fields, List.of("id"))),
                doc(null, object(fields, List.of()))), "RESPONSE_REQUIRED_CHANGED"));
    }
    @Test void nestedArrayResponseFieldRemoval() {
        var old = Map.<String, Object>of("type", "array", "items",
                object(Map.of("id", primitive("string")), List.of()));
        var next = Map.<String, Object>of("type", "array", "items", object(Map.of(), List.of()));
        var report = analyze(doc(null, old), doc(null, next));
        assertTrue(has(report, "RESPONSE_FIELD_REMOVED"));
        assertEquals("response/200/items/id", report.breakingChanges().getFirst().location());
    }
    @Test void enumDirections() {
        var narrow = Map.<String, Object>of("type", "string", "enum", List.of("A"));
        var wide = Map.<String, Object>of("type", "string", "enum", List.of("A", "B"));
        assertTrue(has(analyze(doc(wide, narrow), doc(narrow, wide)), "REQUEST_ENUM_CHANGED"));
        assertTrue(has(analyze(doc(wide, narrow), doc(narrow, wide)), "RESPONSE_ENUM_CHANGED"));
        assertEquals(Risk.LOW, analyze(doc(narrow, wide), doc(wide, narrow)).risk());
        assertTrue(has(analyze(doc(null, narrow), doc(null, primitive("string"))), "RESPONSE_ENUM_CHANGED"));
    }
    @Test void nullableDirections() {
        var nullable = Map.<String, Object>of("type", "string", "nullable", true);
        var report = analyze(doc(nullable, primitive("string")), doc(primitive("string"), nullable));
        assertTrue(has(report, "REQUEST_NULLABILITY_CHANGED"));
        assertTrue(has(report, "RESPONSE_NULLABILITY_CHANGED"));
        assertEquals(Risk.LOW, analyze(doc(primitive("string"), nullable), doc(nullable, primitive("string"))).risk());
    }
    @Test void changedTypes() {
        var report = analyze(doc(primitive("string"), primitive("string")),
                doc(primitive("integer"), primitive("integer")));
        assertTrue(has(report, "REQUEST_TYPE_CHANGED"));
        assertTrue(has(report, "RESPONSE_TYPE_CHANGED"));
    }
    @Test void localReferenceMatchesInline() {
        var a = new HashMap<>(doc(null, Map.of("$ref", "#/components/schemas/Order")));
        a.put("components", Map.of("schemas", Map.of("Order", primitive("string"))));
        assertEquals(Risk.NONE, analyze(a, doc(null, primitive("string"))).risk());
    }
    @Test void externalRecursiveAndMissingReferencesRejected() {
        assertThrows(IllegalArgumentException.class, () -> analyze(doc(null, Map.of("$ref", "https://example.com/schema")), doc(null, primitive("string"))));
        var recursive = new HashMap<>(doc(null, Map.of("$ref", "#/components/schemas/Loop")));
        recursive.put("components", Map.of("schemas", Map.of("Loop", Map.of("$ref", "#/components/schemas/Loop"))));
        assertThrows(IllegalArgumentException.class, () -> analyze(recursive, recursive));
        var missing = doc(null, Map.of("$ref", "#/components/schemas/Missing"));
        assertThrows(IllegalArgumentException.class, () -> analyze(missing, missing));
    }
    @Test void unsupportedSchemasAndVersionsRejected() {
        for (var schema : List.of(Map.<String, Object>of("type", "string", "minLength", 1),
                Map.<String, Object>of("allOf", List.of(primitive("string"))),
                Map.<String, Object>of("type", "object", "additionalProperties", false))) {
            assertThrows(IllegalArgumentException.class, () -> analyze(doc(null, schema), doc(null, schema)));
        }
        var a = new HashMap<>(doc(null, primitive("string"))); a.put("openapi", "3.1.0");
        assertThrows(IllegalArgumentException.class, () -> analyze(a, a));
    }
    @Test void parametersAndSecurityRejected() {
        var a = new HashMap<>(doc(null, primitive("string"))); a.put("security", List.of());
        assertThrows(IllegalArgumentException.class, () -> analyze(a, a));
        var b = Map.<String, Object>of("openapi", "3.0.3", "paths",
                Map.of("/orders", Map.of("parameters", List.of())));
        assertThrows(IllegalArgumentException.class, () -> analyze(b, b));
    }
    @Test void nestingLimitAndMalformedSchemas() {
        Map<String, Object> nested = primitive("string");
        for (int i = 0; i < 35; i++) nested = Map.of("type", "array", "items", nested);
        var a = doc(null, nested);
        assertThrows(IllegalArgumentException.class, () -> analyze(a, a));
        var b = doc(null, Map.of("type", "string", "nullable", "true"));
        assertThrows(IllegalArgumentException.class, () -> analyze(b, b));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(null));
    }
    @Test void requiredBodyAndStatuses() {
        var old = doc(null, primitive("string"));
        var next = Map.<String, Object>of("openapi", "3.0.3", "paths", Map.of("/orders", Map.of("post",
                Map.of("requestBody", Map.of("required", true, "content", Map.of("application/json", Map.of("schema", primitive("string")))),
                        "responses", Map.of("201", content(primitive("string")))))));
        var report = analyze(old, next);
        assertTrue(has(report, "REQUEST_BODY_REQUIRED"));
        assertTrue(has(report, "RESPONSE_STATUS_REMOVED"));
        assertTrue(has(report, "RESPONSE_STATUS_ADDED"));
    }
    @Test void responseBodyChangesAndOptionalAdditions() {
        var noBody = Map.<String, Object>of("openapi", "3.0.3", "paths", Map.of("/orders",
                Map.of("post", Map.of("responses", Map.of("200", Map.of("description", "No body"))))));
        assertTrue(has(analyze(doc(null, primitive("string")), noBody), "RESPONSE_BODY_REMOVED"));
        assertTrue(has(analyze(noBody, doc(null, primitive("string"))), "RESPONSE_BODY_ADDED"));
        assertEquals(Risk.LOW, analyze(doc(null, object(Map.of(), List.of())),
                doc(null, object(Map.of("id", primitive("string")), List.of()))).risk());
    }
}
