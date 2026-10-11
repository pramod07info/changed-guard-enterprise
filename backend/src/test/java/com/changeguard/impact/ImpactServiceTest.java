package com.changeguard.impact;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.changeguard.impact.ImpactReport.Risk;

class ImpactServiceTest {
    private final ImpactService service = new ImpactService();
    private final Map<String, Map<String, String>> before =
            Map.of("GET /payments", Map.of("id", "string", "amount", "number"));
    private final Map<String, List<String>> graph = Map.of(
            "orders", List.of("payments", "payments"),
            "checkout", List.of("orders"), "payments", List.of("checkout"));
    private ImpactRequest request(Map<String, Map<String, String>> after) {
        return new ImpactRequest("payments", before, after, graph);
    }

    @Test void removedFieldAndCycle() {
        var report = service.analyze(request(Map.of("GET /payments", Map.of("id", "string"))));
        assertEquals(Risk.HIGH, report.risk());
        assertEquals("RESPONSE_FIELD_REMOVED", report.breakingChanges().getFirst().code());
        assertEquals(List.of("orders", "checkout"),
                report.potentialConsumers().stream().map(ImpactReport.Consumer::service).toList());
        assertEquals(List.of("payments", "orders", "checkout"), report.potentialConsumers().getLast().path());
        assertTrue(report.requiresReview());
        assertEquals(1, report.recommendedTests().size());
    }
    @Test void removedEndpoint() {
        assertEquals("ENDPOINT_REMOVED", service.analyze(request(Map.of())).breakingChanges().getFirst().code());
    }
    @Test void changedType() {
        var report = service.analyze(request(Map.of("GET /payments", Map.of("id", "integer", "amount", "number"))));
        assertEquals("RESPONSE_TYPE_CHANGED", report.breakingChanges().getFirst().code());
    }
    @Test void unchangedHasNoImpact() {
        var report = service.analyze(request(before));
        assertEquals(Risk.NONE, report.risk());
        assertTrue(report.potentialConsumers().isEmpty());
    }
    @Test void additiveIsLowRisk() {
        var report = service.analyze(request(Map.of("GET /payments", Map.of("id", "string", "amount", "number", "status", "string"))));
        assertEquals(Risk.LOW, report.risk());
        assertFalse(report.requiresReview());
    }
    @Test void invalidData() {
        assertThrows(IllegalArgumentException.class, () -> service.analyze(null));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ImpactRequest(" ", before, before, null)));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(new ImpactRequest("payments", null, before, null)));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(request(Map.of("GET /payments", Map.of("id", "uuid")))));
    }
    @Test void nullTypeIsRejected() {
        var fields = new HashMap<String, String>(); fields.put("id", null);
        assertThrows(IllegalArgumentException.class, () -> service.analyze(request(Map.of("GET /payments", fields))));
    }
    @Test void missingGraphIsAllowed() {
        assertTrue(service.analyze(new ImpactRequest("payments", before, Map.of(), null)).potentialConsumers().isEmpty());
    }
}
