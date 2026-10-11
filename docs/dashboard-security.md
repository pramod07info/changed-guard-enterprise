# Dashboard, authentication and tenant isolation — phase 4

## Identity provider setup

The backend is an OAuth2 JWT resource server. It does not issue tokens or
store user passwords. Configure a trusted identity provider such as your
organization's OIDC provider with an access token containing:

| Claim | Requirement |
| --- | --- |
| iss | Exact configured issuer |
| aud | Includes configured API audience |
| sub | Nonempty user identity |
| exp | Required future expiration, subject to Spring Security clock skew |
| tenant_id | String matching [A-Za-z0-9][A-Za-z0-9._-]{0,99} |

The provider must assign tenant_id from trusted membership data. Never let
users choose or edit that claim. This implementation accepts one tenant per
token and grants tenant members the same analysis permissions. RBAC is planned.
Signing keys come only from the server-configured JWKS URL. The default
Nimbus decoder accepts RS256; issuer/timestamps/audience/required claims are
validated. No client-supplied issuer or signing key is trusted.

Set CHANGEGUARD_JWT_ISSUER, CHANGEGUARD_JWKS_URI and CHANGEGUARD_JWT_AUDIENCE
alongside CHANGEGUARD_DB_PASSWORD. Use HTTPS identity endpoints outside local
development. Compose does not provision an identity provider.

All API and metrics routes now require Authorization: Bearer ACCESS_TOKEN.
Only GET /actuator/health is public. This deliberately changes the earlier
unauthenticated development API. Existing example curl commands need an
Authorization header. For example:

```sh
curl -H "Authorization: Bearer $CHANGEGUARD_ACCESS_TOKEN" -H "Content-Type: application/json" --data-binary @examples/openapi-change.json http://localhost:8080/api/v1/analyses
```

Use trusted access tokens from your provider; no real tokens belong in source
or logs. This resource server is stateless and accepts bearer headers, not
session cookies. CSRF is disabled for that authentication model.

## Dashboard

Docker Compose serves the dashboard at http://localhost:3000. Connect with an
identity-provider access token. The first request to /api/v1/session verifies
identity before loading the workspace.

Features: editable before/after OpenAPI JSON, dependency graph input, preview,
analyze-and-save, risk/findings/impact paths, suggested tests, saved report
history with pagination and snapshot viewing. Inputs are rendered as text.

Tokens remain in React memory; no localStorage, sessionStorage or cookies
are used. Disconnect and refresh clear token/report/history state. An HTTP
401 disconnects the workspace. Requests have a timeout, and responses from
a disconnected session cannot populate a newer workspace.

This initial connection flow uses a token, not a browser OIDC sign-in flow.
SSO authorization-code/PKCE login, automatic refresh and server-side revocation
are future integration work.

For frontend development use Node 22:

```sh
cd frontend
npm ci
npm test
npm run build
npm run dev
```

Vite forwards /api to localhost:8080; CHANGEGUARD_API_PROXY can override it.
Browser smoke tests use mocked API responses and do not replace backend
JWT/isolation tests. Run npx playwright install chromium then npm run test:e2e.
They cover connect, preview/save, history, snapshots, logout, token expiry and
mobile overflow.
Compose uses Nginx to serve the app and forward /api to the backend, keeping
browser requests same-origin. Production deployment must terminate HTTPS.

## Tenant boundaries and existing data

The authenticated tenant is resolved by TenantContext in the service layer.
Every report/snapshot read and history query includes tenant_id. UUIDs alone
do not grant access. Other tenants' IDs return 404. Composite foreign keys
also prevent reports from linking to another tenant's snapshots.
This is application-query isolation plus relational integrity, not PostgreSQL
row-level security. SQL access by administrators remains privileged.

Flyway V2 quarantines all pre-tenant rows under __legacy_unassigned__. JWT
validation forbids that tenant value. Existing history is retained but cannot
be read through the API until an administrator verifies ownership and performs
a coordinated reassignment of report/snapshot rows. No automatic ownership
guessing or user-facing reassignment endpoint exists.

Tests cover signed tokens, expiry, invalid signatures, issuer/audience claims,
anonymous requests, cross-tenant API access, composite foreign keys and
upgrade of existing unowned history. HTTP fixture decoders exist only in tests.

Audit trails, RBAC, tenant membership administration, retention/redaction and
OIDC sign-in remain planned.
