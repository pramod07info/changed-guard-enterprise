# Implementation changelog

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

Validation: Java 21 Maven verify will run through the existing GitHub CI workflow.

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
