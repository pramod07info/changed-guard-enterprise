# ChangeGuard Enterprise

Java 21 / Spring Boot foundation for a microservices change impact platform.
This repository is the new implementation; the earlier Python MVP remains in
pramod07info/ChangeGuard.

## Implemented

- React/TypeScript dashboard for contract analysis, saved history and snapshots.
- Built-in OAuth 2 authorization server, PostgreSQL users, PKCE dashboard login and tenant-scoped persistence.
- Project/service catalog, immutable versioned baselines and baseline-linked comparison evidence.

- Normalized response contract comparison: endpoint removal, field removal, type changes.
- Cycle-safe dependency traversal with potential consumers and shortest impact paths.
- REST API, structured HTTP 400 errors, Actuator health and Prometheus metrics.
- OpenAPI 3.0 JSON import with nested schemas, local references and directional request/response checks.
- Unit and HTTP tests, Docker build and local Compose entry point.

PostgreSQL now stores contract snapshots and analysis history through new endpoints.
AI inference and external repository integrations remain planned.

## Run with Java 21 and Maven 3.9+

First set CHANGEGUARD_DB_PASSWORD and SPRING_PROFILES_ACTIVE=local, as
described in [dashboard and security setup](docs/dashboard-security.md), then start PostgreSQL with
docker compose up -d postgres from repository root.
See [persistence setup](docs/persistence.md) for environment variables and endpoints.

```sh
cd backend
mvn verify
mvn spring-boot:run
```

Or from repository root: `docker compose up --build`.
Open http://localhost:3000 and click Sign in. Local user: `pramod` / `ChangeGuard123!`.
The Docker build skips database tests; GitHub CI runs them against PostgreSQL.
From repository root in another terminal:

```sh
curl -H "Authorization: Bearer $CHANGEGUARD_ACCESS_TOKEN" -H "Content-Type: application/json" --data-binary @examples/change.json http://localhost:8080/api/v1/impact
curl http://localhost:8080/actuator/health
curl -H "Authorization: Bearer $CHANGEGUARD_ACCESS_TOKEN" http://localhost:8080/actuator/prometheus
```

Dependencies are consumer -> provider lists. Contracts are maps of endpoint
names to response field types (string, integer, number, boolean, object, array).
The original endpoint accepts normalized JSON contracts. For embedded OpenAPI
documents use [the OpenAPI endpoint](docs/openapi.md). Potential impact
does not prove endpoint usage. Additive fields are LOW risk; strict consumers
may reject them. Review flags are advisory.

## Validation status

Catalog/baseline verification passed: 79 backend tests against PostgreSQL,
8 frontend unit tests, 3 browser smoke tests, and frontend/backend builds.
[Backend CI](https://github.com/pramod07info/changed-guard-enterprise/actions/runs/38208003994)
and [frontend CI](https://github.com/pramod07info/changed-guard-enterprise/actions/runs/38208003993).
Browser smoke tests use OAuth/API fixtures; backend tests perform real database
login, PKCE exchange, catalog/baseline persistence and tenant isolation. Docker Compose runtime has
not been exercised here. See [authentication setup](docs/dashboard-security.md).

## Planned stack

See [catalog and baseline workflow](docs/catalog-baselines.md) for new dashboard
features, tenant-scoped endpoints and current publication permission limits.

See [architecture](docs/architecture.md) for implementation status and sequence.
See [backend package conventions](docs/backend-architecture.md) and
[implementation changelog](docs/CHANGELOG.md) for code organization and changes.
The local API uses JWT authentication and tenant query isolation and is bound
to localhost. Production deployment still needs HTTPS, limits, audit, RBAC and
identity lifecycle integration. No AWS or Kubernetes resources are provisioned.
