# Target architecture and delivery sequence

Start with a modular backend; extract workers when workload requires it.
Deterministic evidence determines compatibility. AI explains findings and
suggests tests; it must not override compatibility rules or deploy changes.

| Component | Technology | Status |
| --- | --- | --- |
| Backend | Java 21, Spring Boot | First impact API implemented |
| AI agents | Spring AI; LangChain4j alternative | Planned; choose one after compatibility validation |
| Frontend | React, TypeScript | Dashboard, analysis/history/snapshot and webhook/check management views implemented |
| Messaging | Apache Kafka | Planned asynchronous analysis jobs |
| Database | PostgreSQL, pgvector | Tenant-scoped history, users, projects, services and baselines implemented; audit and pgvector planned |
| Dependency analysis | JavaParser, Neo4j | Planned source extraction and persisted graph |
| API contracts | OpenAPI, Pact | Bounded OpenAPI 3.0 JSON import implemented; Pact planned |
| Database schemas | Normalized PostgreSQL JSON | Bounded comparison, risk findings, evidence/history and dashboard implemented; SQL parsing/introspection and DB PR checks planned |
| Kafka contracts | Avro, Schema Registry | Planned directional event compatibility |
| Integrations | GitHub, GitLab, Jenkins | Public PR comparison, signed webhooks, durable analysis/publication queues and opt-in App check writes implemented; private access, GitLab and Jenkins planned |
| Infrastructure | Docker, Kubernetes, AWS | Docker build supplied; Kubernetes/AWS planned |
| Observability | OpenTelemetry, Prometheus | Prometheus endpoint supplied; tracing planned |

1. OpenAPI import with directional request/response rules implemented; extend constraints and parameters next.
2. Snapshot/report persistence, tenant isolation, project/service catalog and immutable baselines implemented.
3. React UI, JWT authentication and tenant-scoped persistence implemented; built-in OAuth 2 PKCE sign-in implemented; RBAC planned.
4. Extract Java call dependencies using JavaParser; persist verified edges in Neo4j.
5. Add idempotent Kafka jobs, retry policies and dead-letter handling.
6. Add Pact and Avro compatibility checks with contract registry adapters.
7. Add evidence-backed AI explanations and pgvector retrieval.
8. Add repository/CI adapters, OpenTelemetry tracing and deployment manifests.

pgvector stores semantic retrieval embeddings; Neo4j stores dependency edges.
They serve separate queries. User-supplied repository code must not be executed
by the analyzer; source analysis should be sandboxed and bounded.

## Current catalog phase

Services are registered with owner labels and repository metadata. Baselines
are explicitly published by tenant members, selected by UUID, and linked to
saved comparison evidence. Publication is not yet a role-based approval flow.
Baseline-aware public GitHub PR comparison is implemented as an opt-in read-only
adapter; catalog registration itself remains metadata-only. Signed webhooks and
operator-approved GitHub App checks now automate analysis and publication. Required
checks need repository rule setup; private access and evidence-backed source
dependency discovery remain planned. See github-pr-comparison.md, github-webhooks.md
and github-app-checks.md for boundaries and setup.

## Deployment capabilities

Implemented modules now share immutable deployment flags in `config.FeatureFlags`.
`controller.FeatureController` exposes an authenticated `dto.FeatureManifest`.
`config.FeatureGateConfiguration` registers `service.FeatureGateFilter` after Spring
Security/firewall and before controller work. Disabling features preserves OAuth/JWT
and tenant boundaries. Analysis services suppress optional graph/test enrichments;
GitHub signature/App settings/workers/publisher use effective flags and stop disabled
work. Repositories and persisted evidence retain their existing schemas/tenant filters.

The React workspace loads and validates the complete backend manifest with the session,
then supplies capabilities to module components. Missing flags fail closed. These are
startup deployment settings, not user-editable permissions or runtime kill switches.
See [feature-flags.md](feature-flags.md) for all 13 flags, dependencies and limitations.
