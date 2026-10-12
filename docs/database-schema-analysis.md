# PostgreSQL schema snapshot comparison

This phase adds deterministic comparison of **normalized PostgreSQL JSON snapshots**,
with a dashboard and tenant-scoped saved history. It does not parse SQL/Flyway files,
connect to a customer database, execute migrations, infer renames, or automatically
include database findings in GitHub checks. Current GitHub checks still analyze OpenAPI
contracts. Customer migration automation and combined risk policy are later phases.

## Supported snapshot format

Before and after snapshots have exactly `dialect` and `tables` fields:

```json
{
  "dialect": "postgresql",
  "tables": {
    "public.customers": {
      "columns": {"id": {"type": "uuid", "nullable": false}},
      "primaryKey": ["id"]
    },
    "public.payments": {
      "columns": {
        "id": {"type": "uuid", "nullable": false},
        "customer_id": {"type": "uuid", "nullable": true},
        "reference": {"type": "varchar", "nullable": true, "length": 100},
        "amount": {"type": "numeric", "nullable": false, "precision": 10, "scale": 2, "hasDefault": false}
      },
      "primaryKey": ["id"],
      "unique": {"uq_reference": ["reference"]},
      "foreignKeys": {
        "fk_customer": {"columns": ["customer_id"], "referencesTable": "public.customers", "referencesColumns": ["id"]}
      }
    }
  }
}
```

- Table names are schema-qualified (`schema.table`). Identifiers use lowercase ASCII
  letters/digits/underscores, start with a letter/underscore and have at most 63 characters.
  Case/qualification normalization and quoted identifiers are not inferred.
- Supported types: `smallint`, `integer`, `bigint`, `numeric`, `varchar`, `text`,
  `boolean`, `date`, `timestamp`, `timestamptz`, `uuid`, `jsonb`. Type aliases,
  SERIAL/identity, arrays, domains and custom/enum types are rejected.
- `nullable` is required and strictly boolean. `hasDefault` is optional boolean,
  defaulting to false. This records presence only; default expressions are not accepted
  or compared. Required column additions still require review when hasDefault=true.
- VARCHAR requires integer `length` 1..10485760. NUMERIC without precision/scale is
  unbounded; if either is supplied, both are required: precision 1..1000, scale 0..precision.
  Negative scale and scale beyond precision are outside this bounded subset. Dimensions
  on unrelated types and unsupported fields are rejected rather than ignored.
- Optional `primaryKey` is an ordered list (empty means none). Its columns must exist
  and be non-nullable. Optional `unique` maps constraint names to nonempty column lists.
- Optional `foreignKeys` maps constraint names to definitions shown above. References
  must exist in the same snapshot and reference a modeled primary/unique key. Lists
  have equal lengths, no duplicates and matching base types in this subset. FK actions,
  deferrability and match variants are not modeled. Constraint names cannot overlap.
- A snapshot can have no tables. A table requires 1..100 columns; snapshots allow at most
  100 tables, 2000 total columns, 200 named unique/FK constraints per table and 1 MiB of
  compact serialized JSON. Unknown metadata is rejected.

Snapshot authors are responsible for accurately representing the schemas. Validated
JSON metadata is not proof of a valid deployable PostgreSQL migration.

## Findings and risk policy

| Change | Risk |
| --- | --- |
| Removed table or column | HIGH |
| Changed base column type (including integer widening) | HIGH; application mappings/casts need review |
| Changed nullability in either direction | HIGH; legacy writes or reader assumptions may be affected |
| Changed default presence | HIGH; default expression semantics are unknown |
| Added non-nullable column, even with a declared default | HIGH; validate backfill, defaults and legacy inserts |
| Changed primary, unique or foreign key definition/addition/removal | HIGH |
| Reduced VARCHAR length | HIGH |
| Increased VARCHAR length | LOW, advisory |
| Reduced NUMERIC integer or fractional capacity, or unbounded → bounded | HIGH |
| Expanded NUMERIC capacities or bounded → unbounded | LOW, advisory |
| Added nullable column or new table | LOW, advisory |
| No modeled changes | NONE |

NUMERIC compares integer digits (`precision - scale`) and fractional digits separately.
A scale increase can reduce integer capacity even at the same precision. Type changes
are conservative findings rather than assuming database casts guarantee client compatibility.
The overall risk is the maximum severity; HIGH requires review. Findings are deterministically
ordered by table/column/constraint names. Renames appear as removals/additions, and constraint
name/order changes may produce conservative findings. LOW/NONE is not migration approval.

Potential consumer paths reuse the cycle-safe service graph. Edges are consumer → provider.
The logical service identifier uses the catalog identifier shape (1..100 characters).
Graph inputs allow at most 500 consumer entries, 5000 edges and 64 KiB; dependency names
must be exact identifiers without surrounding whitespace. Paths do not prove table usage.
Suggestions describe validation to perform; the application does not execute those tests.

## API and persistence

All routes require the existing JWT bearer session. The request fields are only
`service`, `before`, `after` and optional `dependencies`; tenant IDs and connection
credentials are not accepted. The raw request is bounded to 2 MiB + 64 KiB before
JSON deserialization, with strict DTO fields and trailing-token checks.

| Method / route | Behavior |
| --- | --- |
| POST `/api/v1/impact/database` | Preview report; no persistence |
| POST `/api/v1/database-analyses` | Analyze and atomically save supplied snapshots, graph and report; returns 201 |
| GET `/api/v1/database-analyses/{id}` | Tenant-scoped report plus original submitted evidence |
| GET `/api/v1/database-analyses?service=payments&limit=20&offset=0` | Tenant-scoped lightweight history summaries |

Use `examples/database-schema-change.json` for preview/save. Invalid/unsupported
metadata returns 400, oversized bodies 413, anonymous requests 401, and foreign/missing
saved IDs 404. Pagination uses limit 1..100 and offset 0..100000, with stable created_at/id
ordering. Previewing or validation failure creates no history row.

Flyway V9 adds `database_schema_analysis` in ChangeGuard's own PostgreSQL database.
No customer DB credentials, connection or DDL are used. History is separate from existing
OpenAPI snapshots, baselines, webhook analysis and publication queues. One transactional
insert retains before/after/graph/report together. Detail reads are tenant-filtered; list
projections avoid transporting schema JSON. Save timestamps use PostgreSQL microsecond
precision for stable round-trip evidence. There is no schema baseline/catalog lifecycle,
update/delete API, numeric combined score or GitHub schema provenance in this phase.

## Dashboard

After sign-in, open **Database schema changes** and use **Load database example**.
Edit service, before/after normalized schemas and optional dependencies, then preview
or **Analyze database & save**. The panel shows findings, per-finding risk, potential
consumers and suggested validation. It displays a separate database history with saved
schema/graph evidence. Selecting history restores the submitted documents in the editors.
The report is labelled as the last submitted comparison if editors are changed afterward.

On mobile, findings use stacked labelled rows; long schemas remain in scrollable editors
and saved JSON evidence. Database reports do not overwrite the OpenAPI report panel.
Bearer tokens use the existing in-memory session. Sign-out/401 clears database views and
late callbacks after unmount are ignored. No SQL execution, credential input or automatic
remote action is added to the UI.

## Verification and follow-on work

Service tests cover removals, additions, default/nullability/type changes, capacity direction,
constraints, invalid schemas, graph bounds and deterministic ordering. HTTP/repository tests
cover preview versus save, tenant isolation, original evidence round-trips, malformed/oversized
input and pagination against real PostgreSQL. Frontend tests cover input bounds and browser
preview/save/history/evidence/error/session/mobile flows. Browser APIs/OAuth are fixtures;
no customer DB migration or live frontend/backend/GitHub end-to-end test is performed.

Future work: bounded SQL/Flyway parser, read-only schema export, actual query/table dependency
discovery, versioned schema baselines, PR evidence and combined API/DB/Kafka check policy,
operational migration/backfill validation and retention/RBAC.

PostgreSQL references informing the conservative policy:
- https://www.postgresql.org/docs/18/ddl-alter.html
- https://www.postgresql.org/docs/18/ddl-constraints.html
