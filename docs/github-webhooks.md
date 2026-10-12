# GitHub webhook-triggered analysis

This phase adds signed webhook intake and durable automatic public-PR analysis.
It does **not** publish GitHub checks, comments or block merges. Those require
GitHub App installation authentication and a write adapter in a later phase.
The existing manual public PR endpoints remain available.

## Enable locally

Set these on the backend, alongside the existing PostgreSQL and OAuth settings:

```text
CHANGEGUARD_GITHUB_ENABLED=true
CHANGEGUARD_GITHUB_WEBHOOKS_ENABLED=true
CHANGEGUARD_GITHUB_WEBHOOK_SECRET=<random secret of at least 32 UTF-8 bytes>
```

Generate a random secret outside source control (for example `openssl rand -hex 32`).
The server fails startup if webhooks are enabled with a short secret or without
GitHub reads enabled. Webhooks are disabled by default. This is an operator-owned
shared secret; do not expose it to tenant users or put it in the frontend.
Use HTTPS for GitHub delivery and retain TLS certificate verification.
The loopback backend needs an explicitly configured ingress/reverse proxy to be
reachable by GitHub. No tunnel, webhook, credentials or GitHub settings are created
by this implementation.

## Register a baseline binding

Sign in, create a project/service with a public GitHub repository URL, and publish
an immutable baseline. With that user's bearer access token:

```http
POST /api/v1/baselines/{baselineId}/github-webhooks
Authorization: Bearer <access-token>
Content-Type: application/json

{"contractPath":"api/openapi.json","dependencies":{"orders":["payments"]}}
```

The 201 response includes the subscription ID, repository, baseline ID and
`deliveryPath`. Tenant and publisher come exclusively from verified JWT claims.
The repository comes from the tenant's catalog service. A baseline/path pair can
be registered only once (409 on duplicates). The binding is immutable; list up to
100 bindings with `GET /api/v1/baselines/{baselineId}/github-webhooks`.
Registration currently uses existing tenant membership permissions; no new RBAC
or per-subscription disable/delete UI is provided. Disable the global feature
and remove the GitHub webhook if an operator needs to stop deliveries.

Configure a **repository webhook** in GitHub Settings with:

- Payload URL: your HTTPS backend origin plus the returned `deliveryPath`.
- Content type: `application/json`.
- Secret: the same operator-owned backend secret.
- Events: Pull requests.

Supported actions are `opened`, `reopened`, `synchronize`, and `ready_for_review`.
Signed pings return `pong`; unsupported events/actions return `ignored`.
No PR code is executed or checked out. Private repositories remain unsupported.

## Intake and durable processing

1. The controller reads at most 1 MiB plus one byte, rejecting oversized bodies
   even when no Content-Length is provided.
2. HMAC-SHA256 is checked against the exact raw bytes using a constant-time digest
   comparison before JSON parsing or subscription lookup. Missing/invalid
   signatures return 401. The only bearer-exempt route is POST to the delivery
   path; registration, lists, status and history remain bearer-protected.
3. The signed PR repository/base repository must match the registered repository.
   The positive PR number and 40-character head SHA are validated.
4. A PostgreSQL job is persisted, then the endpoint returns 202 without GitHub
   network I/O. Duplicate delivery UUIDs with matching event/body hashes return
   `duplicate`; reuse with different content returns 409. Deduplication is per
   subscription/delivery, not across different GitHub delivery IDs.
5. The worker polls every five seconds, claiming one eligible job per poll with
   `FOR UPDATE SKIP LOCKED`, a random ownership token and a 120-second lease.
   It carries tenant identity from the persisted subscription, without creating
   fake JWTs or weakening existing tenant validation.
6. Public PR data and contract contents are fetched with the existing bounded,
   fixed-origin adapter outside database transactions. If the fetched PR head
   differs from the event's head, the delivery becomes `SUPERSEDED` and writes
   no evidence. A new synchronize event must analyze the new commit.
7. The worker locks the owned job and atomically commits snapshots, report,
   baseline/source provenance and `SUCCEEDED`. A reclaimed old worker cannot
   save evidence, and a failed transaction cannot leave half-completed history.

Transient GitHub/server or unexpected analysis failures retry after 60 seconds,
up to three attempts. Invalid contracts/bindings and missing public PR/files are
terminal. Crashes recover through lease expiry; an exhausted third lease becomes
`FAILED`. Raw payloads, webhook secrets and upstream exception messages are not
persisted or returned in status. No GitHub code or embedded payload URLs are used.

```http
GET /api/v1/github/webhooks/{subscriptionId}/deliveries/{deliveryUuid}
Authorization: Bearer <access-token>
```

The tenant-scoped result includes state, attempts, analysisId and safe errorCode.
Successful reports appear in the existing history dashboard. There is no new
frontend webhook management panel in this phase.

States: `QUEUED`, `PROCESSING`, `SUCCEEDED`, `SUPERSEDED`, `FAILED`.
For success, retrieve the existing `/api/v1/analyses/{analysisId}` endpoint.
A failed/superseded delivery never represents a successful compatibility check.
GitHub does not automatically retry all failed webhook deliveries; operators can
use GitHub's redelivery feature after fixing ingress errors. Matching delivery
redelivery does not reset terminal analysis jobs. A durable retry/replay admin API,
retention cleanup, per-tenant quotas and queue metrics remain future work.

## Verification and limitations

Focused tests exercise signature mutation/Unicode, fail-closed configuration,
HTTP authorization, tenant isolation, repository mismatch, body limits, pings,
duplicate/colliding delivery IDs, persistence, stale heads, retry exhaustion,
invalid-contract rollback and expired lease ownership. HTTP/database tests use
real PostgreSQL and a fixture GitHub client; they do not contact live GitHub.
Run the complete backend suite with `mvn verify` and the database settings from
`persistence.md`. CI supplies PostgreSQL and Java 21.

Official protocol references:
- https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries
- https://docs.github.com/en/webhooks/webhook-events-and-payloads
- https://docs.github.com/en/webhooks/using-webhooks/best-practices-for-using-webhooks

Next integration: GitHub App scoped installation tokens, commit-bound check runs,
check retry/reconciliation, and required-check setup through branch protection.
Merge blocking is not enabled by the webhook receiver itself.
