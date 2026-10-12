package com.changeguard.service;

import static org.junit.jupiter.api.Assertions.*;

import com.changeguard.dto.*;
import com.changeguard.exception.InvalidContractException;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DatabaseSchemaServiceTest {
  final DatabaseSchemaService service =
      new DatabaseSchemaService(new DependencyGraphService(), JsonMapper.builder().build());

  Map<String, Object> column(String type, boolean nullable) {
    return Map.of("type", type, "nullable", nullable);
  }

  Map<String, Object> snapshot(Map<String, Object> columns) {
    return Map.of(
        "dialect", "postgresql", "tables", Map.of("public.payments", Map.of("columns", columns)));
  }

  DatabaseSchemaReport analyze(Map<String, Object> before, Map<String, Object> after) {
    return service.analyze(
        new DatabaseSchemaRequest(
            "payments",
            before,
            after,
            Map.of("orders", List.of("payments"), "checkout", List.of("orders"))));
  }

  @Test
  void unchangedAndExplicitDefaultPresenceNormalizeToNone() {
    var a = snapshot(Map.of("id", column("uuid", false)));
    var b = snapshot(Map.of("id", Map.of("type", "uuid", "nullable", false, "hasDefault", false)));
    var result = analyze(a, b);
    assertEquals(ImpactReport.Risk.NONE, result.risk());
    assertTrue(result.changes().isEmpty());
    assertTrue(result.potentialConsumers().isEmpty());
  }

  @Test
  void removedTableAndColumnAreHighAndTracePotentialConsumers() {
    var a = snapshot(Map.of("id", column("uuid", false), "amount", column("integer", false)));
    var removed = analyze(a, Map.of("dialect", "postgresql", "tables", Map.of()));
    assertEquals("TABLE_REMOVED", removed.changes().getFirst().code());
    assertTrue(removed.requiresReview());
    var result = analyze(a, snapshot(Map.of("id", column("uuid", false))));
    assertEquals("COLUMN_REMOVED", result.changes().getFirst().code());
    assertEquals(ImpactReport.Risk.HIGH, result.risk());
    assertEquals(
        List.of("payments", "orders", "checkout"), result.potentialConsumers().get(1).path());
  }

  @Test
  void addedNullableColumnIsLowButRequiredColumnNeedsReviewEvenWithDefault() {
    var before = snapshot(Map.of("id", column("uuid", false)));
    var low =
        analyze(
            before, snapshot(Map.of("id", column("uuid", false), "note", column("text", true))));
    assertEquals(ImpactReport.Risk.LOW, low.risk());
    for (boolean defaultPresent : List.of(true, false)) {
      var result =
          analyze(
              before,
              snapshot(
                  Map.of(
                      "id",
                      column("uuid", false),
                      "amount",
                      Map.of("type", "integer", "nullable", false, "hasDefault", defaultPresent))));
      assertEquals(ImpactReport.Risk.HIGH, result.risk());
      assertEquals("REQUIRED_COLUMN_ADDED", result.changes().getFirst().code());
    }
  }

  @Test
  void typeNullabilityAndDefaultChangesRequireReview() {
    var before = snapshot(Map.of("amount", column("integer", true)));
    var result =
        analyze(
            before,
            snapshot(
                Map.of("amount", Map.of("type", "bigint", "nullable", false, "hasDefault", true))));
    assertEquals(
        List.of("COLUMN_TYPE_CHANGED", "NULLABILITY_CHANGED", "DEFAULT_PRESENCE_CHANGED"),
        result.changes().stream().map(DatabaseSchemaReport.Change::code).toList());
    assertTrue(
        analyze(
                snapshot(Map.of("id", column("uuid", false))),
                snapshot(Map.of("id", column("uuid", true))))
            .requiresReview());
  }

  @Test
  void varcharNarrowingAndWideningAreDirectional() {
    var before =
        snapshot(Map.of("name", Map.of("type", "varchar", "nullable", true, "length", 100)));
    assertEquals(
        ImpactReport.Risk.HIGH,
        analyze(
                before,
                snapshot(Map.of("name", Map.of("type", "varchar", "nullable", true, "length", 50))))
            .risk());
    assertEquals(
        ImpactReport.Risk.LOW,
        analyze(
                before,
                snapshot(
                    Map.of("name", Map.of("type", "varchar", "nullable", true, "length", 200))))
            .risk());
  }

  Map<String, Object> numeric(Integer precision, Integer scale) {
    var value = new HashMap<String, Object>(column("numeric", false));
    if (precision != null) {
      value.put("precision", precision);
      value.put("scale", scale);
    }
    return snapshot(Map.of("amount", value));
  }

  @Test
  void numericCapacityTracksIntegerAndFractionalDigitsAndUnboundedTransitions() {
    assertEquals(ImpactReport.Risk.HIGH, analyze(numeric(10, 2), numeric(10, 3)).risk());
    assertEquals(ImpactReport.Risk.HIGH, analyze(numeric(10, 2), numeric(10, 1)).risk());
    assertEquals(ImpactReport.Risk.LOW, analyze(numeric(10, 2), numeric(12, 3)).risk());
    assertEquals(ImpactReport.Risk.HIGH, analyze(numeric(null, null), numeric(12, 3)).risk());
    assertEquals(ImpactReport.Risk.LOW, analyze(numeric(10, 2), numeric(null, null)).risk());
  }

  @Test
  void primaryUniqueAndForeignKeyChangesAreDetected() {
    var columns = Map.<String, Object>of("id", column("uuid", false));
    var before =
        Map.<String, Object>of(
            "dialect",
            "postgresql",
            "tables",
            Map.of("public.payments", Map.of("columns", columns)));
    var after =
        Map.<String, Object>of(
            "dialect",
            "postgresql",
            "tables",
            Map.of(
                "public.payments",
                Map.of(
                    "columns",
                    columns,
                    "primaryKey",
                    List.of("id"),
                    "unique",
                    Map.of("uq_id", List.of("id")),
                    "foreignKeys",
                    Map.of(
                        "fk_self",
                        Map.of(
                            "columns",
                            List.of("id"),
                            "referencesTable",
                            "public.payments",
                            "referencesColumns",
                            List.of("id"))))));
    assertEquals(
        List.of("PRIMARY_KEY_CHANGED", "UNIQUE_CONSTRAINT_CHANGED", "FOREIGN_KEY_CHANGED"),
        analyze(before, after).changes().stream().map(DatabaseSchemaReport.Change::code).toList());
    assertEquals(ImpactReport.Risk.HIGH, analyze(after, before).risk());
  }

  @Test
  void rejectsUnsupportedTypesFieldsNullBooleanAndMalformedNames() {
    for (var columns :
        List.of(
            Map.<String, Object>of("id", Map.of("type", "serial", "nullable", false)),
            Map.<String, Object>of("id", Map.of("type", "uuid", "nullable", "false")),
            Map.<String, Object>of(
                "id", Map.of("type", "uuid", "nullable", false, "default", "gen_random_uuid()")),
            Map.<String, Object>of("ID", column("uuid", false))))
      assertThrows(
          InvalidContractException.class, () -> analyze(snapshot(columns), snapshot(columns)));
    assertThrows(
        InvalidContractException.class,
        () ->
            analyze(
                Map.of("dialect", "mysql", "tables", Map.of()),
                snapshot(Map.of("id", column("uuid", false)))));
    assertThrows(
        InvalidContractException.class,
        () ->
            analyze(
                Map.of(
                    "dialect",
                    "postgresql",
                    "tables",
                    Map.of("payments", Map.of("columns", Map.of("id", column("uuid", false))))),
                snapshot(Map.of("id", column("uuid", false)))));
  }

  @Test
  void constraintsMustReferenceValidNonNullableKeysAndExistingTargets() {
    var columns = Map.<String, Object>of("id", column("uuid", true));
    var badPk =
        Map.<String, Object>of(
            "dialect",
            "postgresql",
            "tables",
            Map.of("public.payments", Map.of("columns", columns, "primaryKey", List.of("id"))));
    assertThrows(InvalidContractException.class, () -> analyze(badPk, badPk));
    var badFk =
        Map.<String, Object>of(
            "dialect",
            "postgresql",
            "tables",
            Map.of(
                "public.payments",
                Map.of(
                    "columns",
                    columns,
                    "foreignKeys",
                    Map.of(
                        "fk_missing",
                        Map.of(
                            "columns",
                            List.of("id"),
                            "referencesTable",
                            "public.missing",
                            "referencesColumns",
                            List.of("id"))))));
    assertThrows(InvalidContractException.class, () -> analyze(badFk, badFk));
  }

  @Test
  void rejectsInvalidPrecisionAndTableBounds() {
    assertThrows(InvalidContractException.class, () -> analyze(numeric(10, 11), numeric(10, 11)));
    var tables = new HashMap<String, Object>();
    for (int i = 0; i < 101; i++)
      tables.put("public.table_" + i, Map.of("columns", Map.of("id", column("uuid", false))));
    assertThrows(
        InvalidContractException.class,
        () ->
            analyze(
                Map.of("dialect", "postgresql", "tables", tables),
                snapshot(Map.of("id", column("uuid", false)))));
    assertThrows(InvalidContractException.class, () -> service.analyze(null));
  }

  @Test
  void findingsAreStableAcrossMapInsertionOrder() {
    var columns = new LinkedHashMap<String, Object>();
    columns.put("z", column("text", true));
    columns.put("a", column("integer", false));
    var before = snapshot(columns);
    var after = snapshot(Map.of("id", column("uuid", false)));
    assertEquals(
        List.of("columns/a", "columns/z", "columns/id"),
        analyze(before, after).changes().stream()
            .map(DatabaseSchemaReport.Change::location)
            .toList());
  }

  @Test
  void invalidConstraintColumnsAndTargetsFailClosed() {
    var columns = Map.<String, Object>of("id", column("uuid", false));
    for (Object pk : List.of(List.of("missing"), List.of("id", "id"))) {
      var schema =
          Map.<String, Object>of(
              "dialect",
              "postgresql",
              "tables",
              Map.of("public.payments", Map.of("columns", columns, "primaryKey", pk)));
      assertThrows(InvalidContractException.class, () -> analyze(schema, schema));
    }
    var targetWithoutKey =
        Map.<String, Object>of(
            "dialect",
            "postgresql",
            "tables",
            Map.of(
                "public.payments",
                Map.of(
                    "columns",
                    columns,
                    "foreignKeys",
                    Map.of(
                        "fk_self",
                        Map.of(
                            "columns",
                            List.of("id"),
                            "referencesTable",
                            "public.payments",
                            "referencesColumns",
                            List.of("id"))))));
    assertThrows(InvalidContractException.class, () -> analyze(targetWithoutKey, targetWithoutKey));
  }

  @Test
  void graphBoundsAndNamesAreValidated() {
    var schema = snapshot(Map.of("id", column("uuid", false)));
    assertThrows(
        InvalidContractException.class,
        () ->
            service.analyze(
                new DatabaseSchemaRequest(
                    "payments", schema, schema, Map.of(" orders ", List.of("payments")))));
    assertThrows(
        InvalidContractException.class,
        () ->
            service.analyze(
                new DatabaseSchemaRequest(
                    "payments",
                    schema,
                    schema,
                    Map.of("orders", Collections.nCopies(5001, "payments")))));
  }
}
