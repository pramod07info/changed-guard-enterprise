# Target architecture and delivery sequence

Start with a modular backend; extract workers when workload requires it.
Deterministic evidence determines compatibility. AI explains findings and
suggests tests; it must not override compatibility rules or deploy changes.

| Component | Technology | Status |
| --- | --- | --- |
| Backend | Java 21, Spring Boot | First impact API implemented |
| AI agents | Spring AI; LangChain4j alternative | Planned; choose one after compatibility validation |
| Frontend | React, TypeScript | Planned |
| Messaging | Apache Kafka | Planned asynchronous analysis jobs |
| Database | PostgreSQL, pgvector | Planned reports, audit, tenants and retrieval |
| Dependency analysis | JavaParser, Neo4j | Planned source extraction and persisted graph |
| API contracts | OpenAPI, Pact | Bounded OpenAPI 3.0 JSON import implemented; Pact planned |
| Kafka contracts | Avro, Schema Registry | Planned directional event compatibility |
| Integrations | GitHub, GitLab, Jenkins | Planned authenticated repository and CI adapters |
| Infrastructure | Docker, Kubernetes, AWS | Docker build supplied; Kubernetes/AWS planned |
| Observability | OpenTelemetry, Prometheus | Prometheus endpoint supplied; tracing planned |

1. OpenAPI import with directional request/response rules implemented; extend constraints and parameters next.
2. Persist projects, contract snapshots and analysis reports in PostgreSQL.
3. Build React report UI, authenticate users and enforce tenant boundaries.
4. Extract Java call dependencies using JavaParser; persist verified edges in Neo4j.
5. Add idempotent Kafka jobs, retry policies and dead-letter handling.
6. Add Pact and Avro compatibility checks with contract registry adapters.
7. Add evidence-backed AI explanations and pgvector retrieval.
8. Add repository/CI adapters, OpenTelemetry tracing and deployment manifests.

pgvector stores semantic retrieval embeddings; Neo4j stores dependency edges.
They serve separate queries. User-supplied repository code must not be executed
by the analyzer; source analysis should be sandboxed and bounded.
