# Dashboard authentication and tenant isolation

## ChangeGuard OAuth 2 server

ChangeGuard now hosts its own Spring Security OAuth 2 authorization server.
Keycloak, external JWKS configuration and the fixed local token are removed.
The dashboard uses Authorization Code with mandatory S256 PKCE. It is a public
client (`changeguard-dashboard`) with no browser client secret or password grant.
Click Sign in, authenticate on the backend login page, and return to the dashboard.
The frontend exchanges the code at `/oauth2/token` through its same-origin proxy.

Backend protocol routes: `/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`.
`/api/v1/auth/config` exposes endpoint URLs and the public client ID, never keys.
Spring Security owns OAuth protocol handling and the CSRF-protected login/logout
forms; application controllers do not reimplement the protocol.

## Local run

Set `CHANGEGUARD_DB_PASSWORD` and run PostgreSQL:

```sh
docker compose up -d postgres
```

For an IDE backend run, set `SPRING_PROFILES_ACTIVE=local`, retaining database
connection variables. Remove the old `CHANGEGUARD_JWT_*` variables and any
`local-dev` profile. Run Java 21/Maven:

```sh
cd backend
mvn verify
mvn spring-boot:run
```

Alternatively set the environment profile in your terminal before these commands.
PowerShell: `$env:SPRING_PROFILES_ACTIVE="local"`.
For all services: `docker compose up --build`, then open http://localhost:3000.
The Compose file enables the local profile explicitly. Keycloak can be stopped;
no service on port 8180 is needed.

Flyway V3 creates `application_user` with a constrained tenant ID and encoded
password column. V4 in `db/local` inserts the local user:

| Username | Password | Tenant |
| --- | --- | --- |
| pramod | ChangeGuard123! | local-team |

The password is stored as a BCrypt hash, never plaintext. The local migration
is enabled only by `application-local.yml`. Do not promote this local database
into production; deployed databases use the normal migration location and no
seed user. If an existing local database is switched to another profile, Flyway
may report the local V4 migration as missing: retain the local migration location
for that development database, or use a separate clean deployment database.

The local profile generates an RSA signing key on startup; backend restart
invalidates previously issued tokens. Sign in again after a restart.

## Configuration and deployment boundaries

| Variable | Default / purpose |
| --- | --- |
| CHANGEGUARD_AUTH_ISSUER | http://localhost:8080; canonical public backend URL |
| CHANGEGUARD_FRONTEND_URL | http://localhost:3000; logout destination |
| CHANGEGUARD_FRONTEND_ORIGINS | Exact allowed local frontend origins for token CORS |
| CHANGEGUARD_REDIRECT_URIS | Exact callback URLs on local ports 3000/5173 |
| CHANGEGUARD_SIGNING_JWK | Private RSA JWK JSON with kid, >=2048 bits; required outside exclusive local profile |

Outside local use HTTPS public URLs, exact deployment origins/callbacks and a
secret-managed stable signing key. No signing key belongs in source control.
Tokens last 30 minutes, validate signature, issuer, expiry, subject, audience
`changeguard-api` and tenant membership. The tenant claim is read from the user
row at issuance, never from a request/header. API calls require Bearer headers
and never use login session cookies. Login/logout retain CSRF protection.

Current OAuth client registrations, login sessions and authorization codes are
in memory. Restart loses pending authorizations; this phase supports a single
backend instance. Durable authorization/session storage, key rotation, refresh,
rate limiting, account administration, RBAC and audit remain follow-up work.
The public browser client does not receive refresh tokens; sign in again after
expiry. Logout clears the backend session after the confirmation form; existing
signed tokens remain valid until expiry (no token revocation list).

## Dashboard storage

Access tokens stay in React memory. Session storage holds only the temporary
PKCE verifier/state, expires after ten minutes, and is removed on callback.
The callback validates state and age before exchanging a code, clears the URL,
and does not expose credentials in logs. Sign out clears workspace data;
HTTP 401 requires a new sign-in. Local storage is not used.
Vite and Nginx forward `/api` and `/oauth2/token` to the backend.

## Tenant boundaries

TenantContext resolves verified claims. All report/snapshot reads and history
queries filter tenant_id; another tenant's UUID returns 404. Composite foreign
keys enforce consistent report/snapshot tenants. Flyway V2 retains old unowned
history under `__legacy_unassigned__`, which cannot be used in an access token.
No automatic ownership assignment is performed.

## Verification

Backend coverage includes database login, wrong passwords, CSRF, PKCE failures,
code replay, signed token/session use, cookie rejection for APIs, fixed-token
rejection and existing JWT/tenant isolation tests. Frontend unit tests cover PKCE
and callback state/expiry; browser fixtures cover login callback, analysis,
history, snapshots, sign-out and expired sessions. Browser fixtures supplement
the backend protocol tests and do not prove a live browser/backend deployment.
