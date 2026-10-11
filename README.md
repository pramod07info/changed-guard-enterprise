# ChangeGuard Enterprise

Java 21 / Spring Boot foundation for a microservices change impact platform.
This repository is the new implementation; the earlier Python MVP remains in
pramod07info/ChangeGuard.

## Implemented

- Normalized response contract comparison: endpoint removal, field removal, type changes.
- Cycle-safe dependency traversal with potential consumers and shortest impact paths.
- REST API, structured HTTP 400 errors, Actuator health and Prometheus metrics.
- Unit and HTTP tests, Docker build and local Compose entry point.

No AI inference, persistence or external integrations run in this first slice.

## Run with Java 21 and Maven 3.9+

```sh
cd backend
mvn verify
mvn spring-boot:run
```

Or from repository root: `docker compose up --build`.
The Docker build runs tests before packaging.
From repository root in another terminal:

```sh
curl -H "Content-Type: application/json" --data-binary @examples/change.json http://localhost:8080/api/v1/impact
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/prometheus
```

Dependencies are consumer -> provider lists. Contracts are maps of endpoint
names to response field types (string, integer, number, boolean, object, array).
These are normalized JSON contracts, not OpenAPI documents. Potential impact
does not prove endpoint usage. Additive fields are LOW risk; strict consumers
may reject them. Review flags are advisory.

## Validation status

Java 21, Maven and Docker were unavailable in the authoring environment.
GitHub Actions successfully ran Java 21 Maven verify for foundation commit
`985948f761e7a514e37f7913469ce4f405d1058c`:
https://github.com/pramod07info/changed-guard-enterprise/actions/runs/38138773579
The Docker image build has not been executed.

## Planned stack

See [architecture](docs/architecture.md) for implementation status and sequence.
The local API has no authentication or tenant isolation and is bound to
localhost. Production deployment requires authentication, limits, audit and
tenant isolation. No AWS or Kubernetes resources are provisioned.
