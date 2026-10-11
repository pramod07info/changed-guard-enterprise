# PostgreSQL analysis history — phase 3

PostgreSQL 17, Spring JDBC and Flyway store immutable snapshot/report rows.
JDBC repositories use bound parameters and explicit SQL. entity records are
persistence models; DTO records define API responses. No JPA annotations are needed.
Flyway V1 creates contract_snapshot, analysis_report, foreign keys and a history index.

## Local setup

Set CHANGEGUARD_DB_PASSWORD to a local development password in your terminal
or a git-ignored .env file. Never commit .env or real credentials.

```sh
docker compose up --build
```

Compose starts PostgreSQL with a named volume and waits for its health check
before starting the backend. Both exposed ports bind to localhost.
Stopping Compose preserves saved data. Do not use docker compose down -v
unless you intend to delete the development database.

For Maven development, first run docker compose up -d postgres, then run
mvn verify and mvn spring-boot:run from backend. Set CHANGEGUARD_DB_PASSWORD
in the shell as well: Maven does not read the Compose .env file.
Optional variables: CHANGEGUARD_DB_URL and CHANGEGUARD_DB_USER.
Application startup now requires a reachable PostgreSQL database.

## Endpoints

| Endpoint | Behavior |
| --- | --- |
| POST /api/v1/analyses | Accept OpenApiRequest; analyze and atomically save two snapshots plus report; 201 with Location |
| GET /api/v1/analyses/{id} | Read saved report and snapshot IDs; missing ID returns 404 |
| GET /api/v1/analyses?service=payments&limit=20&offset=0 | Service history, newest first; limit 1..100, offset 0..100000 |
| GET /api/v1/snapshots/{id} | Read original semantic JSON document; missing ID returns 404 |

Use examples/openapi-change.json as the POST body. The original /impact and
/impact/openapi routes still analyze without writing history.
New save/history endpoints limit service names to 200 characters.

Snapshots preserve semantic JSON, not whitespace or field ordering. Each save
creates new UUIDs; there is no deduplication or idempotency key yet.
Both snapshots, original dependency graph and analysis report commit in one
transaction. Validation errors or write failures leave no partial records.
History is offset-paginated with deterministic created_at/id ordering; it is
not a stable cursor under concurrent insertion.

## Verification and limitations

GitHub CI runs Maven verify against a fresh PostgreSQL service. Repository,
service and HTTP tests cover JSONB round trips, foreign keys, pagination,
rollback, invalid input, 201/400/404 behavior and the existing analysis suites.
Use a dedicated test database: tests insert and remove their own fixture rows.
Docker packages the app with tests skipped because image builds do not have
a database. Database tests run separately in CI. Container runtime itself
has not been verified in the authoring environment.

No authentication, tenant isolation, retention policy or snapshot-redaction
policy exists yet. Keep the development API local. pgvector and AI retrieval
are planned and are not needed for report history.
