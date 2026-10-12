# GitHub App check publication and required merge checks

The webhook analysis queue now has a separate durable check-publication queue.
Completed analysis evidence is reused; a failed GitHub write does not rerun analysis.
This feature is opt-in and uses an operator-owned GitHub App, not a user PAT.
The implementation does not create an App, installation, webhook, credentials,
branch protection or rulesets on your behalf.

## Create and install the App

1. In GitHub Settings > Developer settings > GitHub Apps, create your ChangeGuard
   App. Configure its webhook URL/secret for the existing subscription delivery
   path and subscribe to Pull request events. Alternatively retain the repository
   webhook already configured in `github-webhooks.md`; installation IDs in the
   webhook payload are not used to grant write access.
2. Grant repository permissions **Checks: Read and write**, **Pull requests: Read**
   for App webhook PR events, and the implicit **Metadata: Read** permission.
   Install it only on the public repository being analyzed. Private repository
   reads are not introduced in this phase. No Contents write permission is needed.
3. Record the numeric App ID and installation ID. Download its RSA private key.
   Convert GitHub's downloaded PKCS#1 key to the PKCS#8 format used by this server:

   ```sh
   openssl pkcs8 -topk8 -nocrypt -in downloaded-app.pem -out changeguard-app-pkcs8.pem
   ```

   Keep both files outside the repository, restrict access, and mount the PKCS#8
   file read-only into the backend. The server accepts RSA keys of at least 2048
   bits and PEM files no larger than 16 KiB. Restart to rotate the key.
4. Register a tenant-owned baseline webhook subscription as documented in
   `github-webhooks.md`. Review its repository, immutable baseline, contract path
   and dependency graph before approving it for checks. Users cannot approve
   subscriptions or choose installations through API request bodies.

## Backend configuration

```text
CHANGEGUARD_GITHUB_ENABLED=true
CHANGEGUARD_GITHUB_WEBHOOKS_ENABLED=true
CHANGEGUARD_GITHUB_WEBHOOK_SECRET=<random operator-owned secret of at least 32 UTF-8 bytes>
CHANGEGUARD_GITHUB_APP_ENABLED=true
CHANGEGUARD_GITHUB_APP_ID=<numeric-app-id>
CHANGEGUARD_GITHUB_APP_PRIVATE_KEY_FILE=<absolute-path-to-pkcs8-pem-in-backend>
CHANGEGUARD_GITHUB_APP_BINDINGS={"<subscription-uuid>":<installation-id>}
```

Bindings are JSON, with at most 100 canonical subscription UUID keys and positive
installation ID values. Multiple services/baselines use separate bindings and
separate check names. This operator allowlist protects against a tenant registering
an arbitrary public repository and gaining access to your App's write credentials.
Enabled configuration fails startup without webhooks, a positive App ID or a valid
RSA key. Existing webhook configuration also requires GitHub reads enabled.

Compose passes these environment values but does not automatically mount a private
key. Add a local override (outside source control) with a read-only volume:

```yaml
services:
  backend:
    volumes:
      - ./local-secrets/changeguard-app-pkcs8.pem:/run/secrets/changeguard-app.pem:ro
    environment:
      CHANGEGUARD_GITHUB_APP_PRIVATE_KEY_FILE: /run/secrets/changeguard-app.pem
```

The path in the environment refers to the backend container filesystem, not the
host filesystem. Do not put key contents, JWTs or installation tokens into the
frontend, source code, logs or status responses.

## Publication behavior

- Polling reconciles terminal `SUCCEEDED`, `FAILED` and `SUPERSEDED` webhook
  deliveries for approved subscriptions into Flyway V8's publication table.
  This also backfills prior terminal deliveries when an operator enables a binding.
  Signed intake continues returning 202 without making GitHub writes.
- A worker claims one publication per poll with `SKIP LOCKED`, a random ownership
  token and a five-minute lease. Tokens and remote responses are never stored.
- The App signs an RS256 JWT with 60-second clock allowance and nine-minute expiry.
  It verifies the repository's current installation and App ID from GitHub before
  minting an installation token scoped to that repository's independently fetched
  numeric ID and **checks:write only** (plus GitHub's implicit metadata access).
- Check writes use the fixed `https://api.github.com` origin, no redirects, a
  five-second connect timeout, twenty-second response limits and bounded bodies.
- The stable check name is **`ChangeGuard / <subscription-uuid>`**. The checked SHA
  is the webhook delivery's exact head SHA, never a branch name or a newer head.
  A deterministic external ID identifies the subscription/delivery pair.
- Supported analysis with no review requirement, no HIGH risk and no breaking
  findings produces `success`. Review-required/HIGH/breaking reports produce `failure`.
  Failed or superseded analysis also produces `failure`; an incomplete analysis
  cannot issue compatibility approval. LOW-risk supported changes can pass.
- The check summary includes counts and the saved analysis UUID. It deliberately
  covers only the supported OpenAPI rules and supplied dependency graph, not DB,
  Kafka, complete OpenAPI compatibility or actual downstream test execution.

Before creating a check, the publisher lists runs for the same SHA/name, with
`filter=all`, and reconciles its App ID and external ID. An ambiguous POST timeout
can therefore retry by updating the existing run rather than blindly creating
another. The response's App, SHA, name, ID, external ID and conclusion are verified.
The list is bounded to 100 matching runs; an incomplete/ambiguous list fails closed.
GitHub does not provide an atomic create idempotency key: publication is at least
once with reconciliation, not a guarantee of exactly one remote check under every
crash or eventually consistent API response. The immutable delivery outcome keeps
repeated writes to that delivery's commit consistent.

Transient/network/rate-limit failures retry after sixty seconds, up to three
attempts. Credential, scope, identity and invalid-payload failures stop immediately.
An exhausted lease becomes `FAILED`. A missing check or failed publication must
never be treated as a compatibility pass. Removing an operator approval prevents
further queued writes; revoke the installation/key in GitHub to revoke issued
credentials themselves.

## Status and explicit publication retry

Both endpoints require the tenant's bearer token and do not accept installation IDs:

```http
GET /api/v1/github/webhooks/{subscription}/deliveries/{delivery}/check
Authorization: Bearer <access-token>
```

Returns publication state, attempts, checkRunId, checkName and a safe errorCode.
States are `QUEUED`, `PROCESSING`, `PUBLISHED`, `FAILED`. `PUBLISHED` means a result
was delivered, not that the compatibility result passed. The analysis outcome is
available through the existing delivery/history APIs. Status is 404 for another
tenant or for an unapproved delivery that has no publication row.

```http
POST /api/v1/github/webhooks/{subscription}/deliveries/{delivery}/check/retry
Authorization: Bearer <access-token>
```

After fixing credentials, scope or service issues, this resets a FAILED publication
for its approved binding. It never modifies analysis, snapshots or a PUBLISHED
result. Anonymous retry is 401, foreign tenant retry is 404, and non-failed retry
is 409. A disabled/removed binding returns 503. Existing tenant membership controls
apply; separate RBAC/admin approval controls remain future work.

## Make it block merges

1. Run a real PR delivery and confirm the expected App published a check on the
   current PR head. Inspect success and breaking-change cases before rollout.
2. In repository Settings > Rules > Rulesets (or branch protection), target your
   merge branch and enable **Require status checks to pass**.
3. Select the exact **`ChangeGuard / <subscription-uuid>`** name and the expected
   GitHub App as its source, when GitHub offers the source selector. For multiple
   contract bindings, require each corresponding check.
4. Set the rule's enforcement and bypass policy for your repository. Verify a
   breaking-change PR is blocked and a supported compatible PR is allowed.

The application publishes checks; GitHub's configured rules enforce merge blocking.
This phase does not change those rules automatically. Only configured checks are
required; the absence of a rule means check failure alone does not prevent a merge.
Merge-queue `merge_group` events, PR comments, requested check reruns, private repos,
RBAC, publication quotas and retention remain future work. A registration/status/retry
dashboard is now available; see `webhook-dashboard.md`.
If you use a merge queue, do not enable this as a required merge-queue check until
`merge_group` support is implemented.

## Validation

Tests cover JWT signing and clock claims, disabled/invalid key configuration,
repository/permission scoping, installation mismatch, ambiguous create recovery,
wrong commit/App responses, expired tokens, fixed-origin writes, redirects, rate
limits and bounded responses. PostgreSQL HTTP/repository tests cover allowlist
isolation, success/failure policy, publication-only retries, tenant-protected status,
lease expiry, deduplication and composite foreign keys. GitHub is mocked/loopback;
no live App, installation, check write or branch-protection test is performed by CI.
Run `mvn verify` with the database configuration in `persistence.md`.

Official protocol references:
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app
- https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app
- https://docs.github.com/en/rest/checks/runs
- https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches
