package com.changeguard.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.changeguard.exception.InvalidContractException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DependencyGraphServiceTest {
    private final DependencyGraphService graph = new DependencyGraphService();

    @Test void diamondUsesShortestPathWithoutDuplicates() {
        var report = graph.trace("payments", Map.of(
                "orders", List.of("payments"), "audit", List.of("payments"),
                "checkout", List.of("orders", "audit"), "storefront", List.of("checkout")));
        assertEquals(4, report.size());
        var checkout = report.stream().filter(c -> c.service().equals("checkout")).findFirst().orElseThrow();
        assertEquals(2, checkout.distance());
        assertEquals(List.of("payments", "audit", "checkout"), checkout.path());
    }

    @Test void invalidProvidersAndNamesAreDomainFailures() {
        var nullList = new HashMap<String, List<String>>();
        nullList.put("orders", null);
        assertThrows(InvalidContractException.class, () -> graph.trace("payments", nullList));
        assertThrows(InvalidContractException.class, () -> graph.validate(" ", null));
        assertThrows(InvalidContractException.class, () -> graph.validate("payments", Map.of("orders", List.of(" "))));
    }
}
