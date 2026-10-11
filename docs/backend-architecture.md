# Backend architecture conventions

Base package: com.changeguard. Spring Boot scans all layers from
ChangeGuardApplication in that base package.

| Package | Responsibility | Current classes |
| --- | --- | --- |
| controller | HTTP mappings, request/response transport and service delegation | ImpactController, OpenApiController |
| service | Compatibility rules, import validation and dependency traversal | ImpactService, OpenApiService, DependencyGraphService |
| dto | Immutable API request and report records | ImpactRequest, ImpactReport, OpenApiRequest, OpenApiReport |
| exception | Expected failure types and central HTTP error translation | InvalidContractException, ApiErrors |
| repository | Bound-parameter JDBC queries and inserts | AnalysisRepository, ContractSnapshotRepository |
| entity | JDBC persistence models | AnalysisRecord, ContractSnapshot |
| utility | Stateless reusable helpers that do not belong to a service | Not yet needed |
| config | Explicit infrastructure configuration when needed | Currently uses Boot configuration and application.yml |

## Dependency rules

- Controllers depend on services and DTOs. Controllers do not implement compatibility rules.
- Services own business decisions and may depend on repositories once persistence exists.
- Repositories will own database queries; controllers must not query the database directly.
- DTOs contain transport data, not persistence annotations.
- Exception advice translates expected InvalidContractException failures and malformed
  JSON to HTTP 400 using ProblemDetail. Unexpected failures are not relabeled as client errors.
- DependencyGraphService is the shared graph validator and traversal implementation.
  OpenAPI analysis no longer fabricates a normalized-contract change to obtain graph results.
- Keep schema-import helpers private to OpenApiService until another component actually
  needs them. Avoid an unrelated utility collection.
- Do not create placeholder repositories or entities before database implementation.
  PostgreSQL history now uses concrete repository and entity packages.

## Tests and future changes

Service tests live in com.changeguard.service. HTTP integration tests live in
com.changeguard.controller. Test package names follow the component they verify.

New features must follow these boundaries, preserve the existing API unless a
versioned change is documented, and add focused behavior tests. Update
docs/CHANGELOG.md for each implementation change and docs/architecture.md when
technology or implementation status changes.

AnalysisHistoryController delegates history endpoints to AnalysisHistoryService.
The history service owns transactions and JSON serialization; repositories
handle SQL. ResourceNotFoundException maps missing rows to HTTP 404.
See persistence.md for PostgreSQL setup. Authentication and tenant isolation
remain planned.
