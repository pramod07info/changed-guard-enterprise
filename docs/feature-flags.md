# Deployment feature flags

Flags apply to all tenants on a backend deployment and are immutable until restart.
They control every implemented business module; they do not implement future Kafka/Avro,
AI, Slack/Teams, GitLab/Jenkins, RBAC or deployment features.
OAuth login, JWT verification, tenant isolation, session, capability discovery and health
are always enforced/available under their existing authentication rules.
Risk scoring and human-review findings are part of the enabled analyzer and are not
independently suppressible.

## Configuration

Set `changeguard.features.<name>.enabled` in Spring configuration or the environment
variable in the table. All **new feature flags default to true** to preserve existing
behavior. Effective GitHub features remain off unless their existing integration
opt-ins and prerequisites are enabled. Setting a downstream flag to true cannot
override an upstream false flag. Invalid boolean configuration fails startup.

| Name | Environment variable | Controls | Effective prerequisites |
| --- | --- | --- | --- |
| `normalized-analysis` | `CHANGEGUARD_FEATURE_NORMALIZED_ANALYSIS_ENABLED` | Normalized contract analyzer | None |
| `open-api-analysis` | `CHANGEGUARD_FEATURE_OPEN_API_ANALYSIS_ENABLED` | OpenAPI analyzer and manual dashboard | None |
| `analysis-history` | `CHANGEGUARD_FEATURE_ANALYSIS_HISTORY_ENABLED` | OpenAPI saved analyses and contract snapshots | OpenAPI analysis |
| `catalog` | `CHANGEGUARD_FEATURE_CATALOG_ENABLED` | Projects and registered services | None |
| `baselines` | `CHANGEGUARD_FEATURE_BASELINES_ENABLED` | Baseline publication, reads and comparison | Catalog + OpenAPI analysis |
| `database-analysis` | `CHANGEGUARD_FEATURE_DATABASE_ANALYSIS_ENABLED` | PostgreSQL schema analyzer and dashboard | None |
| `database-history` | `CHANGEGUARD_FEATURE_DATABASE_HISTORY_ENABLED` | Database saved analyses and evidence/history | Database analysis |
| `dependency-analysis` | `CHANGEGUARD_FEATURE_DEPENDENCY_ANALYSIS_ENABLED` | Dependency traversal and potential consumers | None |
| `test-recommendations` | `CHANGEGUARD_FEATURE_TEST_RECOMMENDATIONS_ENABLED` | Deterministic suggested validation/tests | None |
| `github-pr` | `CHANGEGUARD_FEATURE_GITHUB_PR_ENABLED` | GitHub PR preview and saved comparison | Baselines + legacy GitHub enable |
| `webhooks` | `CHANGEGUARD_FEATURE_WEBHOOKS_ENABLED` | Subscriptions, signed ingress and analysis worker | GitHub PR + analysis history + legacy webhook enable |
| `github-checks` | `CHANGEGUARD_FEATURE_GITHUB_CHECKS_ENABLED` | GitHub App check queue, publisher and retry/status | Webhooks + legacy GitHub App enable |
| `metrics` | `CHANGEGUARD_FEATURE_METRICS_ENABLED` | Prometheus and Actuator info endpoints | None |

Legacy integration prerequisites are `CHANGEGUARD_GITHUB_ENABLED`,
`CHANGEGUARD_GITHUB_WEBHOOKS_ENABLED`, and `CHANGEGUARD_GITHUB_APP_ENABLED`;
all still default to false. Enabling webhooks also requires a configured signing
secret. Enabling checks requires the real App ID/key and operator-approved subscription
bindings described in [GitHub checks](github-app-checks.md). Credentials are never returned
by capability discovery. Disabled integrations do not load/validate their secret/key,
claim queue work, fetch PRs or publish checks. Existing worker/publisher polling switches
can pause polling independently; they do not hide the enabled module or manual APIs.

### Local PowerShell example

Keep local analysis and catalog enabled, hide automation and Prometheus:

```powershell
$env:CHANGEGUARD_FEATURE_GITHUB_PR_ENABLED="false"
$env:CHANGEGUARD_FEATURE_WEBHOOKS_ENABLED="false"
$env:CHANGEGUARD_FEATURE_GITHUB_CHECKS_ENABLED="false"
$env:CHANGEGUARD_FEATURE_METRICS_ENABLED="false"
# Start/restart the backend after setting these in the same terminal.
cd backend
mvn spring-boot:run
```

Preview-only mode (both API and database):

```powershell
$env:CHANGEGUARD_FEATURE_ANALYSIS_HISTORY_ENABLED="false"
$env:CHANGEGUARD_FEATURE_DATABASE_HISTORY_ENABLED="false"
```

With Compose, set the same variables in `.env` or your shell and recreate the backend:
`docker compose up -d --build backend`. With Kubernetes, add them to the backend
Deployment environment/ConfigMap and roll out new pods. Keep values consistent across
replicas; these are deployment controls, not runtime admin toggles. After backend restart,
sign out and sign in to reload the dashboard manifest. An existing browser may still
show stale controls, but the backend always rejects disabled operations.

## API and dashboard behavior

Authenticated `GET /api/v1/features` returns the 13 effective boolean capabilities in
`FeatureManifest` (camelCase keys). It contains no credentials, bindings or tenant data.
Disabled API routes return **503 application/problem+json**, title `Feature disabled`,
and the effective capability name in `feature`. Authentication runs first: anonymous
business API calls still return 401. Signed webhook POST ingress retains its existing
bearer exemption but returns 503 before signature validation/queueing when disabled.
Health remains available; disabling metrics hides Prometheus/info without stopping
internal metric collection. Flags do not remove Java dependencies, Flyway migrations,
PostgreSQL requirements or authentication startup configuration.

The dashboard loads session and capabilities together and opens only after a complete
boolean manifest is validated. Failed, missing, malformed or unauthorized manifests
leave the workspace unavailable; sign in again to retry. Disabled modules, actions,
graph editors/results and suggestions are hidden, and hidden modules make no requests.
History-off leaves analyzer and baseline/GitHub preview controls available but hides
save/history/snapshot actions. Catalog remains usable for metadata when baselines are
off. Webhooks can analyze/save deliveries with checks off; check setup/status/retry
controls disappear. An all-disabled dashboard shows an explicit empty workspace.

Dependency/test recommendation flags omit those enrichments from new reports. They do
not alter compatibility findings, risk scores or review requirements. Existing stored
reports, dependency evidence and publication outcomes stay unchanged; allowed history
APIs return the original evidence, while the dashboard hides disabled enrichment sections.
A flag is a capability switch, not a field-level data authorization rule.

Queues remain durable while disabled. Re-enabling may resume queued/retryable work and
perform GitHub writes if checks are enabled/approved. An operation already in flight
finishes on its original process; disabling requires restart/redeployment, not instant
cancellation. No data is deleted and no database migration is needed for these flags.

## Implementation and verification

- `config`: immutable `FeatureFlags`, dependency resolution and filter registration
  immediately after Spring Security/firewall.
- `controller` / `dto`: thin feature endpoint and typed immutable manifest.
- `service`: normalized servlet-path gate before controller side effects, analysis
  enrichment decisions, and effective GitHub worker/signature/publisher controls.
- `frontend/src/lib`: strict manifest parser and authenticated loader; components
  receive the server-owned flags, with no client permission toggle.
- Matching config/service/controller tests cover defaults/cascades, disabled route
  variants, 401 preservation, 503/no repository or network work, idle workers, health,
  manifest and preview-only behavior. Browser fixtures cover enabled flows, all-off,
  malformed/expired manifests, preview-only, metadata-only and checks-off automation.

Live GitHub webhook/App subscription setup and merge-rule configuration remain deferred.
