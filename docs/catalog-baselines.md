# Project catalog and contract baselines

## Scope

This phase adds tenant-scoped projects, registered services, versioned OpenAPI
baselines and comparisons against a selected published baseline. Existing
manual comparison/history endpoints remain available. Repository URLs are
metadata only: no cloning, webhook registration, remote reference fetching,
repository execution or GitHub/GitLab writes are performed by these endpoints.

Use the existing OAuth login and local database configuration. Flyway V5 runs
automatically on backend startup; do not edit prior migrations or reset data.
Open the dashboard's **Project catalog & contract baselines** section after login.

## Workflow

1. Create a project.
2. Register a service with an owner/team label and optional HTTPS GitHub/GitLab URL.
3. Publish a supported OpenAPI 3.0 JSON contract with version and environment.
4. Select a published baseline, supply the proposed contract and dependency map.
5. Preview, or analyze and save. A saved result includes its `baselineId` and
   before/after snapshots. History retrieval preserves this association.

Publishing is an explicit action by an authenticated tenant member. Baselines
are immutable through the API: no update/delete endpoint exists, and duplicate
service/environment/version combinations return HTTP 409. This is not yet a
separate approval workflow: every tenant member has publication permission.
Owner labels are informational and do not grant or restrict access. RBAC and
reviewer approval are follow-up work; do not describe these baselines as independently approved.
Comparisons never automatically publish or replace a baseline.

## API

All routes below require `Authorization: Bearer ACCESS_TOKEN`.

| Method | Route | Purpose |
| --- | --- | --- |
| POST / GET | /api/v1/projects | Create or list projects |
| GET | /api/v1/projects/{id} | Read a project |
| POST / GET | /api/v1/projects/{id}/services | Register or list services |
| GET | /api/v1/services/{id} | Read a registered service |
| POST / GET | /api/v1/services/{id}/baselines | Publish or list baseline summaries |
| GET | /api/v1/baselines/{id} | Read an immutable baseline document |
| POST | /api/v1/baselines/{id}/preview | Compare without writing history |
| POST | /api/v1/baselines/{id}/analyses | Compare and save evidence |

Lists accept `limit` (default 20, range 1..100) and `offset` (default 0,
range 0..100000). Baseline list responses omit large document payloads. UUID
resource paths return 404 for both nonexistent and other-tenant records.
Creation returns HTTP 201 with a Location header; invalid input returns 400,
duplicates return 409 and missing authentication returns 401.

Project input:

```json
{"name":"Payments platform"}
```

Service input:

```json
{"name":"payments-api","owner":"platform-team","repositoryUrl":"https://github.com/team/payments"}
```

Baseline publication input:

```json
{"version":"1.0.0","environment":"production","document":{"openapi":"3.0.3","paths":{}}}
```

Comparison input supplies only the candidate and optional dependency map:

```json
{"after":{"openapi":"3.0.3","paths":{}},"dependencies":{"orders":["payments-api"]}}
```

The baseline supplies the service name and before contract; clients cannot
select those values independently on the baseline comparison routes.

## Data and security

V5 adds `catalog_project`, `catalog_service`, `contract_baseline`, and nullable
`analysis_report.baseline_id`. Composite foreign keys enforce same-tenant
project/service/baseline relationships and same-tenant, same-service report
baseline references. Tenant and publisher are resolved from authenticated
claims, not request bodies. SQL reads always include tenant filters.

Project names are unique per tenant. Service names are unique across the entire
tenant, not just a project, because current dependency maps and history use
service names. Names and versions are case-sensitive; outer whitespace is
trimmed. Service/environment identifiers allow letters, digits, dot, underscore
and hyphen, start with a letter/digit, and are limited to 100 characters.
Owner labels/project names are limited to 200 characters; versions to 100.
Contracts must be objects within the existing supported OpenAPI subset and
no larger than 1 MiB when serialized as UTF-8 JSON. Repository URLs cannot
contain credentials, query strings, fragments or non-default explicit ports.

Legacy/manual reports remain unchanged except for the additive nullable
`baselineId` response field; no automatic catalog assignment is performed.
Catalog records are currently register/read only. Renaming, archival, current
baseline pointers, automatic environment selection, repository credentials,
GitHub PR checks, full audit events and role-based approval remain planned.
Baseline selection is always by explicit immutable UUID.

## Tests

Tests cover validation, publisher attribution, input bounds, duplicate versions,
cross-tenant API access, composite database constraints, preview without writes,
saved baseline evidence, unsupported contracts, and existing history migration.
Frontend browser fixtures exercise project/service creation, publication,
comparison, saved evidence, mobile layout and logout. CI runs backend tests
against PostgreSQL and frontend unit/build/browser checks.

Verified implementation commit `7c2f43f7b023fdd06d3e451a921808552d56b026`:
79 backend tests, 8 frontend unit tests and 3 browser smoke tests passed;
frontend/backend builds passed. Backend CI run 38208003994 and frontend CI
run 38208003993. Docker Compose runtime was not exercised in this environment.
