# Built-in OAuth 2 login

- Replaced external identity-provider configuration and token-paste UI with an
  embedded Spring Security authorization server and public PKCE browser client.
- Added PostgreSQL users, BCrypt authentication and a local-only Flyway demo seed.
- Kept signed JWT validation and tenant isolation; removed fixed-token development code.
- Added OAuth protocol/user/callback tests and updated proxies, Compose and CI.
- Documented setup, profile changes, keys, token lifetime and single-instance limits.

# Implementation changelog

## 2026-10-11 — Dashboard and tenant authentication

- Added React/TypeScript dashboard with contract input, preview/save, findings,
  history pagination and snapshot views.
- Added JWT resource-server authentication, explicit issuer/audience/expiry/tenant
  requirements and a verified workspace identity endpoint.
- Added tenant context to history services and mandatory tenant-scoped repositories.
- Added V2 migration to quarantine unowned history and enforce cross-tenant snapshot
  foreign keys. Existing records remain retained but require verified ownership migration.
- Added signed-token, tenant access, database integrity and upgrade tests.
- Adapted existing HTTP/service suites to authenticated test identities.
- Added frontend tests/build workflow and Compose Nginx dashboard service.
- Added Playwright browser smoke tests with controlled API fixtures.
- Added dashboard-security.md with provider configuration and connection limitations.
- Authentication is now required for APIs and metrics; health remains public.

Verified final implementation commit 425fa25536ad9a5c444fc54320a85b7b7334f1be:
47 backend tests passed against PostgreSQL, 4 frontend unit tests passed,
2 browser smoke tests passed, and the frontend production build passed.
Backend CI: https://github.com/pramod07info/changed-guard-enterprise/actions/runs/38145550800
Frontend CI: https://github.com/pramod07info/changed-guard-enterprise/actions/runs/38145550812
Desktop/mobile layout was also visually inspected with fixture data. Docker
Compose runtime and a live identity-provider connection remain unverified.

## 2026-10-11 — PostgreSQL snapshot and analysis history

- Added entity and repository packages with JDBC snapshot/report models and queries.
- Added Flyway V1 schema with JSONB documents, foreign keys and service history index.
- Added AnalysisHistoryService with transactional save of two snapshots, dependencies and report.
- Added save/read/history/snapshot endpoints under /api/v1 and centralized HTTP 404 handling.
- Kept original analysis endpoints free of history writes.
- Added PostgreSQL Compose service, volume, health checks and environment configuration.
- Added PostgreSQL-backed repository/service/controller tests and CI database service.
- Docker packaging skips database tests; CI performs Maven verify against PostgreSQL.
- Added persistence.md and updated architecture documentation.

Validation command: backend Maven verify with a dedicated PostgreSQL database.
Verified 39 tests with zero failures, errors or skipped tests against PostgreSQL:
https://github.com/pramod07info/changed-guard-enterprise/actions/runs/38141417435

## 2026-10-11 — Layered backend refactor

- Replaced the mixed com.changeguard.impact package with controller, service,
  dto and exception packages.
- Moved ImpactController and OpenApiController into controller.
- Moved ImpactService and OpenApiService into service.
- Extracted DependencyGraphService to share graph validation and cycle-safe traversal.
- Moved request/report records into dto and updated cross-package imports.
- Added InvalidContractException and moved centralized ApiErrors into exception.
  Expected input failures still produce HTTP 400; malformed JSON remains HTTP 400.
- Reorganized service and HTTP tests to match their implementation layers.
- Added backend-architecture.md with package responsibilities and dependency rules.
- Added AGENTS.md so future changes follow the same conventions.
- Added focused shared-graph validation and shortest-path tests.
- Repository, entity and utility packages are reserved for concrete future needs;
  this refactor introduces no persistence or placeholder abstractions.
- REST routes and report structure remain unchanged.

Validation: run Java 21 Maven verify from backend. The existing GitHub CI
workflow executes this command on each push and pull request.

## 2026-10-11 — OpenAPI contract import

- Added /api/v1/impact/openapi for embedded OpenAPI 3.0 JSON documents.
- Added directional request/response rules, nested schema comparison and local references.
- Unsupported analyzed constructs are rejected without a partial report.
- Added example input and docs/openapi.md.
- Verified 28 tests with zero failures or errors in GitHub CI.

## 2026-10-11 — Java foundation

- Created Java 21 Spring Boot backend, normalized contract analysis and dependency graph tracing.
- Added REST API, central input error handling, health and Prometheus endpoints.
- Added Maven tests, Docker/Compose configuration and Java 21 GitHub CI.
- Documented the complete target stack and phased delivery sequence.
