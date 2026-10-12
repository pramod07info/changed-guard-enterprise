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
| config | Infrastructure configuration | SecurityConfig, application.yml |
| security | Verified identity/tenant rules | TenantContext, RequiredJwtClaimsValidator |

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
See persistence.md for PostgreSQL setup and dashboard-security.md for identity
configuration. History services require a verified tenant; repositories always
filter by tenant. JWT authentication and tenant-scoped access are implemented.
## Built-in authentication

`AuthorizationServerConfig` configures OAuth protocol and login filter chains,
registered PKCE client, signing keys and token claims. `SecurityConfig` keeps
API bearer validation stateless. `CorsConfig` restricts token endpoint origins.
`UserRepository` reads PostgreSQL users; `DatabaseUserDetailsService` supplies
Spring Security identities. `AuthConfigurationController` returns public endpoint
configuration using `AuthConfiguration`. Flyway V3 creates users; local V4 seeds
a BCrypt demo account. Controllers never issue tokens directly.

## Catalog and baseline boundaries

`CatalogController` delegates metadata operations to `CatalogService` and
baseline lifecycle/comparisons to `BaselineService`. `CatalogRepository` owns
project/service SQL; `BaselineRepository` owns baseline SQL and lightweight
summary read projections. Entities are `CatalogProject`, `CatalogServiceRecord`
and `ContractBaseline`; HTTP request/response records remain in `dto`.
`CatalogInput` is a concrete shared stateless validation helper in `utility`.
`CatalogConflictException` maps expected uniqueness conflicts to 409.

BaselineService validates contracts using OpenApiService before publication.
It loads trusted before-contract evidence and invokes AnalysisHistoryService
transactionally for saved comparisons. The nullable baseline ID is retained by
AnalysisRepository and SavedAnalysis; composite foreign keys enforce its tenant
and service identity. See `catalog-baselines.md` for routes and limitations.
New tests follow controller/service/repository/utility package boundaries.
