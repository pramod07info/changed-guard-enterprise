# Read-only GitHub PR comparison

## Scope and activation

This phase reads a public GitHub pull request and one OpenAPI JSON file at its
pinned head commit, compares it with an explicitly selected published baseline,
and optionally saves the report, snapshots and source provenance.

Set `CHANGEGUARD_GITHUB_ENABLED=true` on the backend and restart. Compose passes
this variable through, defaulting to false. Existing catalog/baseline/manual
comparison flows work without enabling GitHub or making external requests.
Flyway V6 adds nullable GitHub provenance to saved analyses automatically.

No PAT is required or accepted. Only public github.com repositories and public
PR heads are supported. Private repositories require a future tenant-scoped
GitHub App authorization design; a shared server token is deliberately not used.
There is no GitLab/GitHub Enterprise adapter yet. All tenant members currently
have the same analysis permissions, consistent with the existing catalog.

No GitHub comment, check run, commit status, webhook, branch update, merge or
repository-setting write is performed. This is an on-demand comparison, not an
automatic PR deployment gate. No repository code is cloned, compiled or executed.

## Dashboard workflow

1. Register a service with `https://github.com/OWNER/REPO` repository metadata.
2. Publish/select an immutable baseline from the catalog section.
3. Enter a positive PR number and relative JSON contract path (e.g. `api/openapi.json`).
4. Use **Preview GitHub PR** or **Analyze GitHub PR & save**.

The existing dependency-map field supplies potential consumers. The proposed
contract editor is not used for a GitHub PR comparison: the backend reads the
file from GitHub. Before evidence always comes from the selected baseline, not
the PR's base branch. The recorded PR base SHA is provenance only.

## API

Authenticated endpoints:

| Method | Path | Result |
| --- | --- | --- |
| POST | /api/v1/baselines/{id}/github-pr/preview | baselineId, source and report |
| POST | /api/v1/baselines/{id}/github-pr/analyses | SavedAnalysis including baselineId and githubSource |

```json
{"pullNumber":7,"contractPath":"api/openapi.json","dependencies":{"orders":["payments"]}}
```

Repository identity comes from the baseline's registered service, never from
request-supplied URLs. Preview returns 200 and does not persist history. Save
returns 201 and a Location header pointing to `/api/v1/analyses/{id}`. Loading
that saved analysis later retains its source evidence; old/manual analyses have
nullable `githubSource` and are not automatically relabeled.

## Provenance and safety

Source evidence records target repository, head repository (including a public
fork), PR number and URL, base/head commit SHAs, contract path, verified blob SHA
and fetch timestamp. The adapter validates that PR metadata belongs to the
registered target repository. File requests use the recorded head SHA, not a
moving branch name, and never follow metadata download URLs. If the PR advances,
the result still describes the captured commit; run a new comparison for updates.

The Git blob SHA-1 is verified against downloaded bytes. SHA-1 here is Git's blob
identifier, not a token-signing algorithm. JSON must be a single UTF-8 object,
with no trailing tokens, within the existing supported OpenAPI subset.
Contracts are limited to 1 MiB; upstream JSON/base64 responses are bounded to
2 MiB before parsing. Paths are limited to 500 characters and ASCII repository
path characters, with no absolute paths, traversal segments, URL/query syntax or
YAML. Only open PRs are accepted. The GitHub Contents API must report a file
with base64 content; directories, submodules and unsupported encodings fail.

Production outbound origin is fixed to `https://api.github.com`. The HTTP client
sends unauthenticated GET requests only, does not follow redirects, and applies
a 5-second connection timeout and 20-second full-response deadline per request.
Each comparison makes two sequential GitHub requests; no automatic retries are
performed. Rate limiting and access restrictions are surfaced rather than
silently switching credentials or endpoints.

Baseline and catalog reads enforce tenant ownership before any outbound request.
Network fetching happens outside database transactions; successful saves use
the existing atomic history transaction. Composite baseline tenant/service
foreign keys remain in force. V6 requires a baseline whenever GitHub provenance
is present. Invalid input/upstream failures do not publish or overwrite baselines.

| Status | Meaning |
| --- | --- |
| 400 | Invalid input, unsupported PR/file/contract |
| 401 | ChangeGuard access token missing/invalid |
| 404 | Baseline not accessible, or public PR/file not found |
| 502 | Invalid, oversized or unavailable upstream response |
| 503 | Integration disabled, GitHub rate/access restriction, or interrupted request |
| 504 | GitHub request deadline exceeded |

GitHub API rate limits apply to unauthenticated requests. This phase has no
per-tenant quotas, job queue, deduplication, retries, GitHub notifications or
automatic policy status publication. Add these with explicit authorization,
idempotency and tenant-owned integration credentials in a future phase.

## Architecture and tests

GitHubPrController delegates to GitHubPrService. GitHubPrClient and its bounded
HTTP transport live in `integration.github`; GitHubConfig supplies the fixed
origin/client. BaselineService owns the trusted before-contract comparison and
AnalysisHistoryService persists snapshots/report/provenance transactionally.

Adapter tests cover pinned commits, public forks, malformed metadata/base64,
size limits, blob integrity, JSON shape/trailing tokens, paths and redirects.
Loopback HTTP transport tests exercise real bounded reads without GitHub access.
Controller/database tests use a mocked adapter and verify tenant ownership,
no-write failures and saved-source round trips. Browser tests use API fixtures.
A live GitHub request and Docker Compose runtime are not verified by those tests.
