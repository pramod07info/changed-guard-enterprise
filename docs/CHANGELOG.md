# Implementation changelog

## 2026-10-12 — Read-only GitHub PR comparison

- Added opt-in public GitHub PR fetching at an explicit head commit, bounded
  response handling, path validation and Git blob integrity checks.
- Added baseline-aware PR preview/save APIs with persisted source provenance
  through Flyway V6; old/manual reports remain unchanged except nullable source.
- Added dashboard PR number/path controls and source evidence in reports.
- Added adapter, loopback transport, service, controller/database and frontend tests.
- Documented activation and public-only/read-only limits in github-pr-comparison.md.
- No PAT, external GitHub writes, webhook automation or private-repository access.

Verified implementation commit 41fd3f7d923f3cf72a96547a31ca7e5521f0c87e:
102 backend tests passed against PostgreSQL, 13 frontend unit tests and 3 browser
smoke tests passed, and both builds passed. Backend CI run 38209607677; frontend
CI run 38209607678. GitHub tests use fixtures/loopback HTTP, not a live GitHub request.

## 2026-10-12 — Project catalog and immutable baselines

- Added tenant-scoped project/service registration and paginated catalog reads.
- Added immutable, environment/version-scoped baseline publication, validation,
  publisher attribution and baseline-aware preview/save endpoints.
- Added V5 schema and composite tenant/service foreign keys; retained baseline
  provenance in saved history without assigning legacy/manual reports.
- Added dashboard catalog, publication, baseline selection and comparison flows.
- Added controller/service/repository/utility tests and browser workflow coverage.
- Documented the API and limits in catalog-baselines.md; corrected stale tenant
  and project implementation status in architecture.md.
- Publication currently permits all tenant members; repository URLs are metadata
  only, and GitHub PR integration/RBAC remain follow-up work.

Verified implementation commit 7c2f43f7b023fdd06d3e451a921808552d56b026:
79 backend tests passed against PostgreSQL (no failures/errors/skips),
8 frontend unit tests and 3 browser smoke tests passed, and both builds passed.
Backend CI: https://github.com/pramod07info/changed-guard-enterprise/actions/runs/38208003994
Frontend CI: https://github.com/pramod07info/changed-guard-enterprise/actions/runs/38208003993
Browser tests use fixtures; Docker Compose runtime remains unverified here.

## 2026-10-12 — Built-in OAuth 2 login

- Replaced external identity-provider configuration and token-paste UI with an
  embedded Spring Security authorization server and public PKCE browser client.
- Added PostgreSQL users, BCrypt authentication and a local-only Flyway demo seed.
- Kept signed JWT validation and tenant isolation; removed fixed-token development code.
- Added OAuth protocol/user/callback tests and updated proxies, Compose and CI.
- Documented setup, profile changes, keys, token lifetime and single-instance limits.

Verified commit e99b568fc25371ece0c7b4571b25e64aadfe56ae: 56 backend tests
passed against PostgreSQL, 8 frontend unit tests and 2 browser smoke tests
passed, with successful frontend/backend builds. Backend CI run 38175230316;
frontend CI run 38175230334. Browser tests use fixtures; backend OAuth protocol
tests exercise actual database authentication and signed token validation.

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

## GitHub signed webhooks and durable automatic PR analysis

- Added tenant-authenticated immutable baseline/path webhook registrations and lists.
- Added bounded raw-body HMAC-SHA256 delivery authentication; all other APIs retain
  bearer authentication and tenant-scoped reads.
- Added Flyway V7 durable subscription/delivery tables, tenant foreign keys,
  per-delivery idempotency and delivery-ID collision detection.
- Added PostgreSQL queue polling, reclaimable ownership leases, three-attempt retries,
  stale-head rejection and atomic history/completion persistence.
- Added tenant-protected delivery status and safe terminal error codes.
- Added layer-organized tests and `docs/github-webhooks.md` setup/operational guide.
- Preserved manual public-PR comparison. This phase does not publish GitHub checks,
  change branch protection, support private repos or introduce a webhook UI.

## GitHub App commit checks

- Added opt-in RSA App JWT authentication and operator-approved subscription bindings.
- Added repository/installation/App identity verification and checks-only repository
  token scoping, fixed-origin bounded writes and external-ID reconciliation.
- Added Flyway V8 durable check-publication queue with tenant foreign keys, reclaimable
  leases and bounded retries independent of analysis evidence.
- Added commit-bound success/failure checks; failed, superseded or review-required
  analysis cannot pass the check. Added protected publication status/retry endpoints.
- Added integration/controller/repository tests, Compose configuration and App/required
  check setup documentation. Private repositories, merge-group events and automatic
  branch-rule configuration remain outside this phase.

## Webhook automation dashboard

- Added baseline-scoped webhook registration, subscription selection, setup URL/check
  name copy controls and responsive delivery cards to the React dashboard.
- Added recent-delivery pagination, separate analysis/publication states, saved-report
  loading and explicit failed-publication retry without rerunning analysis.
- Added tenant-protected delivery list projection joining existing publication status;
  preserved signed POST ingress and all existing authentication boundaries.
- Added frontend helper/browser tests and backend pagination/tenant/join coverage.
- Documented mobile/session behavior, fixture verification and the remaining operator
  setup in `webhook-dashboard.md`; credentials and App approval remain outside the UI.
