package com.changeguard.service;

import static com.changeguard.dto.ImpactReport.Risk.*;

import com.changeguard.config.FeatureFlags;
import com.changeguard.dto.*;
import com.changeguard.exception.InvalidContractException;
import com.changeguard.utility.CatalogInput;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Bounded metadata comparison. Never generates or executes SQL or opens a target DB connection. */
@Service
public class DatabaseSchemaService {
  private record Column(
      String type,
      boolean nullable,
      boolean hasDefault,
      Integer length,
      Integer precision,
      Integer scale) {}

  private record ForeignKey(
      List<String> columns, String referencesTable, List<String> referencesColumns) {}

  private record Table(
      Map<String, Column> columns,
      List<String> primaryKey,
      Map<String, List<String>> unique,
      Map<String, ForeignKey> foreignKeys) {}

  private final boolean recommendations;
  private final DependencyGraphService graph;
  private final JsonMapper json;

  public DatabaseSchemaService(DependencyGraphService graph, JsonMapper json) {
    this.recommendations = true;
    this.graph = graph;
    this.json = json;
  }

  @Autowired
  public DatabaseSchemaService(DependencyGraphService graph, JsonMapper json, FeatureFlags flags) {
    this.graph = graph;
    this.json = json;
    this.recommendations = flags.manifest().testRecommendations();
  }

  public DatabaseSchemaReport analyze(DatabaseSchemaRequest request) {
    if (request == null) throw invalid("Schema request is required");
    String service = CatalogInput.identifier(request.service(), "service");
    var before = schema(request.before());
    var after = schema(request.after());
    var dependencies =
        request.dependencies() == null ? Map.<String, List<String>>of() : request.dependencies();
    if (dependencies.size() > 500 || json.writeValueAsBytes(dependencies).length > 65536)
      throw invalid("Dependency map exceeds supported bounds");
    graph.validate(service, dependencies);
    int edges = 0;
    for (var entry : dependencies.entrySet()) {
      graphName(entry.getKey());
      edges += entry.getValue().size();
      for (String provider : entry.getValue()) graphName(provider);
    }
    if (edges > 5000) throw invalid("Dependency map exceeds 5000 edges");
    var changes = new ArrayList<DatabaseSchemaReport.Change>();
    for (String name : new TreeSet<>(before.keySet())) {
      Table old = before.get(name), next = after.get(name);
      if (next == null) {
        add(
            changes,
            "TABLE_REMOVED",
            name,
            "table",
            "Existing queries and data can depend on this table",
            HIGH);
        continue;
      }
      for (String column : new TreeSet<>(old.columns().keySet())) {
        Column a = old.columns().get(column), b = next.columns().get(column);
        if (b == null) {
          add(
              changes,
              "COLUMN_REMOVED",
              name,
              "columns/" + column,
              "Existing queries and stored data can depend on this column",
              HIGH);
          continue;
        }
        if (!a.type().equals(b.type()))
          add(
              changes,
              "COLUMN_TYPE_CHANGED",
              name,
              "columns/" + column,
              "Changed type requires validating casts and application mappings",
              HIGH);
        else if (!Objects.equals(a.length(), b.length()))
          add(
              changes,
              "VARCHAR_CAPACITY_CHANGED",
              name,
              "columns/" + column,
              "Validate stored values and client size assumptions",
              b.length() < a.length() ? HIGH : LOW);
        else if (!Objects.equals(a.precision(), b.precision())
            || !Objects.equals(a.scale(), b.scale())) {
          boolean narrows =
              b.precision() != null
                  && (a.precision() == null
                      || b.precision() - b.scale() < a.precision() - a.scale()
                      || b.scale() < a.scale());
          add(
              changes,
              "NUMERIC_CAPACITY_CHANGED",
              name,
              "columns/" + column,
              "Validate integer digits, fractional digits and rounding",
              narrows ? HIGH : LOW);
        }
        if (a.nullable() != b.nullable())
          add(
              changes,
              "NULLABILITY_CHANGED",
              name,
              "columns/" + column,
              b.nullable()
                  ? "Nullable values may violate existing reader assumptions"
                  : "Existing nulls and legacy writes require validation",
              HIGH);
        if (a.hasDefault() != b.hasDefault())
          add(
              changes,
              "DEFAULT_PRESENCE_CHANGED",
              name,
              "columns/" + column,
              "Default presence changed; expression semantics are not modeled",
              HIGH);
      }
      for (String column : new TreeSet<>(next.columns().keySet()))
        if (!old.columns().containsKey(column)) {
          var c = next.columns().get(column);
          add(
              changes,
              c.nullable() ? "COLUMN_ADDED" : "REQUIRED_COLUMN_ADDED",
              name,
              "columns/" + column,
              c.nullable()
                  ? "Review positional queries and result mapping"
                  : "Validate existing-row backfill and legacy inserts, even if a default is"
                      + " declared",
              c.nullable() ? LOW : HIGH);
        }
      if (!old.primaryKey().equals(next.primaryKey()))
        add(
            changes,
            "PRIMARY_KEY_CHANGED",
            name,
            "primaryKey",
            "Validate uniqueness, identity assumptions and referencing consumers",
            HIGH);
      constraints(changes, name, "unique", old.unique(), next.unique());
      constraints(changes, name, "foreignKeys", old.foreignKeys(), next.foreignKeys());
    }
    for (String name : new TreeSet<>(after.keySet()))
      if (!before.containsKey(name))
        add(
            changes,
            "TABLE_ADDED",
            name,
            "table",
            "New table; migration operational behavior is not modeled",
            LOW);
    var risk =
        changes.stream().anyMatch(c -> c.severity() == HIGH)
            ? HIGH
            : changes.isEmpty() ? NONE : LOW;
    var consumers =
        changes.isEmpty() ? List.<ImpactReport.Consumer>of() : graph.trace(service, dependencies);
    var tests = new ArrayList<String>();
    if (!changes.isEmpty()) {
      tests.add(
          "Validate the migration and rollback on a disposable PostgreSQL database with"
              + " representative existing data");
      tests.add("Run legacy reader/writer queries and verify application mappings");
    }
    if (risk == HIGH)
      tests.add("Review backfills, constraint violations and possible data loss before deployment");
    for (var consumer : consumers)
      tests.add("Run database integration tests for potential consumer: " + consumer.service());
    return new DatabaseSchemaReport(
        service,
        risk,
        List.copyOf(changes),
        consumers,
        recommendations ? List.copyOf(tests) : List.of(),
        risk == HIGH,
        List.of(
            "Normalized PostgreSQL metadata only; no SQL parsing, database introspection or"
                + " migration execution",
            "Default expressions, indexes, checks, generated columns, enum types, collations and FK"
                + " actions are outside this subset",
            "Renames are treated as removals/additions; constraint order/name changes may be"
                + " conservative findings",
            "Potential consumers come from the supplied service graph, not verified table access;"
                + " no data or lock-cost analysis"));
  }

  private <T> void constraints(
      List<DatabaseSchemaReport.Change> changes,
      String table,
      String kind,
      Map<String, T> before,
      Map<String, T> after) {
    var names = new TreeSet<>(before.keySet());
    names.addAll(after.keySet());
    for (String name : names)
      if (!Objects.equals(before.get(name), after.get(name)))
        add(
            changes,
            kind.equals("unique") ? "UNIQUE_CONSTRAINT_CHANGED" : "FOREIGN_KEY_CHANGED",
            table,
            kind + "/" + name,
            "Constraint addition, removal or definition change requires data and application"
                + " review",
            HIGH);
  }

  private Map<String, Table> schema(Map<String, Object> document) {
    if (document == null
        || json.writeValueAsString(document).getBytes(StandardCharsets.UTF_8).length > 1048576)
      throw invalid("Each schema document is required and must not exceed 1 MiB");
    keys(document, Set.of("dialect", "tables"));
    if (!"postgresql".equals(document.get("dialect")))
      throw invalid("Only dialect postgresql is supported");
    var raw = object(document.get("tables"), "tables");
    if (raw.size() > 100) throw invalid("At most 100 tables are supported");
    var tables = new TreeMap<String, Table>();
    int count = 0;
    for (var entry : raw.entrySet()) {
      String name = tableName(entry.getKey());
      var value = object(entry.getValue(), "table " + name);
      keys(value, Set.of("columns", "primaryKey", "unique", "foreignKeys"));
      var columnsRaw = object(value.get("columns"), "columns");
      if (columnsRaw.isEmpty() || columnsRaw.size() > 100 || (count += columnsRaw.size()) > 2000)
        throw invalid("Tables require 1..100 columns, at most 2000 columns total");
      var columns = new TreeMap<String, Column>();
      for (var column : columnsRaw.entrySet()) {
        identifier(column.getKey());
        var c = object(column.getValue(), "column");
        keys(c, Set.of("type", "nullable", "hasDefault", "length", "precision", "scale"));
        if (!(c.get("type") instanceof String type)
            || !Set.of(
                    "smallint",
                    "integer",
                    "bigint",
                    "numeric",
                    "varchar",
                    "text",
                    "boolean",
                    "date",
                    "timestamp",
                    "timestamptz",
                    "uuid",
                    "jsonb")
                .contains(type)) throw invalid("Unsupported column type");
        if (!(c.get("nullable") instanceof Boolean nullable))
          throw invalid("nullable must be an explicit boolean");
        boolean defaultPresent = false;
        if (c.containsKey("hasDefault")) {
          if (!(c.get("hasDefault") instanceof Boolean b))
            throw invalid("hasDefault must be boolean");
          defaultPresent = (Boolean) c.get("hasDefault");
        }
        Integer length = null, precision = null, scale = null;
        if (type.equals("varchar"))
          length = integer(c.get("length"), 1, 10485760, "varchar length");
        else if (c.containsKey("length")) throw invalid("length is supported only for varchar");
        if (type.equals("numeric") && (c.containsKey("precision") || c.containsKey("scale"))) {
          precision = integer(c.get("precision"), 1, 1000, "numeric precision");
          scale = integer(c.get("scale"), 0, precision, "numeric scale");
        } else if (!type.equals("numeric")
            && (c.containsKey("precision") || c.containsKey("scale")))
          throw invalid("precision/scale are supported only for numeric");
        columns.put(
            column.getKey(), new Column(type, nullable, defaultPresent, length, precision, scale));
      }
      List<String> pk =
          value.containsKey("primaryKey")
              ? columnList(value.get("primaryKey"), columns, true)
              : List.of();
      if (pk.stream().anyMatch(c -> columns.get(c).nullable()))
        throw invalid("Primary key columns must be non-nullable");
      var unique = new TreeMap<String, List<String>>();
      for (var constraint : optionalMap(value, "unique").entrySet()) {
        identifier(constraint.getKey());
        unique.put(constraint.getKey(), columnList(constraint.getValue(), columns, false));
      }
      var foreign = new TreeMap<String, ForeignKey>();
      for (var constraint : optionalMap(value, "foreignKeys").entrySet()) {
        identifier(constraint.getKey());
        var fk = object(constraint.getValue(), "foreign key");
        keys(fk, Set.of("columns", "referencesTable", "referencesColumns"));
        var local = columnList(fk.get("columns"), columns, false);
        if (!(fk.get("referencesTable") instanceof String target))
          throw invalid("referencesTable must be a qualified table name");
        var referenced = strings(fk.get("referencesColumns"));
        if (local.size() != referenced.size())
          throw invalid("Foreign key column counts must match");
        foreign.put(constraint.getKey(), new ForeignKey(local, tableName(target), referenced));
      }
      if (unique.keySet().stream().anyMatch(foreign::containsKey))
        throw invalid("Named constraints must be unique within a table");
      if (unique.size() + foreign.size() > 200)
        throw invalid("At most 200 named constraints per table are supported");
      tables.put(name, new Table(columns, pk, unique, foreign));
    }
    for (var table : tables.values())
      for (var fk : table.foreignKeys().values()) {
        var target = tables.get(fk.referencesTable());
        if (target == null) throw invalid("Foreign key target must exist in the same snapshot");
        columnList(fk.referencesColumns(), target.columns(), false);
        var refs = new HashSet<>(fk.referencesColumns());
        if (!refs.equals(new HashSet<>(target.primaryKey()))
            && target.unique().values().stream().noneMatch(key -> refs.equals(new HashSet<>(key))))
          throw invalid("Foreign key target must be a modeled primary or unique key");
        for (int i = 0; i < fk.columns().size(); i++)
          if (!table
              .columns()
              .get(fk.columns().get(i))
              .type()
              .equals(target.columns().get(fk.referencesColumns().get(i)).type()))
            throw invalid("Foreign key types must match in this bounded subset");
      }
    return tables;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> object(Object value, String name) {
    if (!(value instanceof Map<?, ?> map)
        || map.keySet().stream().anyMatch(key -> !(key instanceof String)))
      throw invalid(name + " must be a JSON object");
    return (Map<String, Object>) value;
  }

  private Map<String, Object> optionalMap(Map<String, Object> object, String key) {
    return object.containsKey(key) ? object(object.get(key), key) : Map.of();
  }

  private void keys(Map<String, Object> object, Set<String> allowed) {
    if (!allowed.containsAll(object.keySet()))
      throw invalid(
          "Unsupported schema fields: "
              + new TreeSet<>(object.keySet())
                  .stream().filter(k -> !allowed.contains(k)).limit(3).toList());
  }

  private String identifier(String name) {
    if (name == null || !name.matches("[a-z_][a-z0-9_]{0,62}"))
      throw invalid(
          "Schema identifiers must use lowercase ASCII letters, digits or underscores, at most 63"
              + " characters");
    return name;
  }

  private String tableName(String name) {
    if (name == null || name.split("\\.", -1).length != 2)
      throw invalid("Table names must be schema-qualified, such as public.payments");
    for (String part : name.split("\\.")) identifier(part);
    return name;
  }

  private List<String> strings(Object value) {
    if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 100)
      throw invalid("Constraint columns require 1..100 names");
    var result = new ArrayList<String>();
    for (Object item : list) {
      if (!(item instanceof String name)) throw invalid("Column references must be strings");
      identifier(name);
      result.add(name);
    }
    if (new HashSet<>(result).size() != result.size())
      throw invalid("Constraint columns cannot repeat");
    return List.copyOf(result);
  }

  private List<String> columnList(Object value, Map<String, Column> columns, boolean allowEmpty) {
    if (allowEmpty && value instanceof List<?> list && list.isEmpty()) return List.of();
    var result = strings(value);
    if (!columns.keySet().containsAll(result))
      throw invalid("Constraint columns must exist in the table");
    return result;
  }

  private int integer(Object value, int min, int max, String name) {
    if (!(value instanceof Number n)
        || !(n instanceof Byte
            || n instanceof Short
            || n instanceof Integer
            || n instanceof Long
            || n instanceof java.math.BigInteger)) throw invalid(name + " must be an integer");
    var number = new java.math.BigInteger(n.toString());
    if (number.compareTo(java.math.BigInteger.valueOf(min)) < 0
        || number.compareTo(java.math.BigInteger.valueOf(max)) > 0)
      throw invalid(name + " is outside supported bounds");
    return number.intValue();
  }

  private void add(
      List<DatabaseSchemaReport.Change> changes,
      String code,
      String table,
      String location,
      String explanation,
      ImpactReport.Risk risk) {
    changes.add(new DatabaseSchemaReport.Change(code, table, location, explanation, risk));
  }

  private void graphName(String value) {
    if (!value.equals(CatalogInput.identifier(value, "dependency service")))
      throw invalid("Dependency identifiers cannot include surrounding whitespace");
  }

  private InvalidContractException invalid(String message) {
    return new InvalidContractException(message);
  }
}
