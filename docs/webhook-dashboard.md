# Webhook automation dashboard

The dashboard now manages subscriptions and shows recent webhook analysis/check
publication outcomes. Backend credentials and App approvals remain operator-owned.
This UI does not configure GitHub or enable merge blocking by itself.

## Use the dashboard

1. Sign in and open **Project catalog & contract baselines**.
2. Load/select a project, registered GitHub service and published baseline.
3. Open **Webhook automation**. Load existing subscriptions, or enter a relative
   JSON contract path and dependency map and select **Register webhook subscription**.
   The graph is copied into the new immutable subscription. Later comparison edits
   do not alter it. Duplicate baseline/path registrations return the backend's 409.
4. Select a subscription. Enter your externally reachable **Public HTTPS origin**
   to assemble the webhook URL. The origin must have no credentials, path, query or
   fragment. Nothing is fetched from that origin by the dashboard. A localhost
   URL cannot receive GitHub deliveries. Copy the URL and exact required-check name;
   if clipboard access is unavailable, select/copy the read-only fields manually.
5. Follow the displayed GitHub setup instructions and `github-webhooks.md` /
   `github-app-checks.md`. The webhook secret, App private key, App ID and installation
   approvals are configured outside the UI. Registration does not prove they are enabled.
6. Use **Refresh deliveries** to read current progress. Pages contain up to 20 records
   and previous/next navigation. There is no automatic polling, background retry or
   remote setting change from opening this panel.
7. **Open saved analysis** loads the persisted compatibility report and snapshot
   actions into the existing report panel. **Open GitHub check** opens a verified
   public GitHub run link when a run ID is available.
8. **Retry check publication** appears only for FAILED publication rows. It requests
   the existing tenant-protected retry API and does not rerun analysis or change saved
   snapshots. QUEUED, PROCESSING and PUBLISHED publications have no retry control.

## State meanings

Analysis and GitHub publication are separate:

| UI state | Meaning |
| --- | --- |
| Analysis queued / Analyzing | The durable analysis worker has not finished |
| Analysis saved | A report was saved; its compatibility result may require review |
| Newer commit detected | The delivery was superseded; no new evidence was saved |
| Analysis failed | Analysis did not complete successfully |
| Check queued / Publishing check | Publication is pending or in progress |
| Result published | A result reached GitHub; this does not mean the result passed |
| Publication failed | Remote delivery failed; explicit publication retry is available |
| Not queued | No publication row exists yet; approval/enablement or a terminal analysis outcome may still be missing |

Error codes and attempt counts are shown without credentials or upstream payloads.
A published check can be a failure, including failed/superseded analyses. Open the
check or saved report to inspect the outcome. Missing publication is not interpreted
as a compatibility approval.

## Read API added for the UI

```http
GET /api/v1/github/webhooks/{subscriptionId}/deliveries?limit=20&offset=0
Authorization: Bearer <tenant-access-token>
```

The subscription must belong to the verified JWT tenant. Anonymous requests return
401; unknown/foreign subscription IDs return 404. Pagination accepts limit 1..100
and offset 0..100000. Results sort by created_at DESC, delivery_id DESC and include
analysis state/attempts, saved analysis ID, safe error code, PR number, head SHA,
creation timestamp, and nullable tenant-matched check publication status. The SQL
projection joins existing tables and does not read contract/report JSON or secrets.
This GET is protected; only the existing HMAC-authenticated POST delivery route is
bearer-exempt. No schema migration or existing endpoint change is required.

## Session and mobile behavior

Registration, delivery loading, report loading and retries use the existing in-memory
bearer session. A 401 disconnects and clears the workspace. Subscription changes
clear the delivery view; old responses/errors are discarded after a selection change
or unmount. No webhook secret, private key or installation token is accepted, returned
or persisted by this UI. The HTTPS origin and selected subscription live only in
component memory and clear on sign-out or baseline changes.

The setup form, long URLs/IDs, delivery cards and actions adapt to narrow screens.
Labels, read-only fields, status/alert announcements and keyboard focus styles are
provided. No native GitHub mobile app functionality is required by the dashboard.

## Verification

Unit tests cover registration shapes/path/graph validation, explicit safe origins,
stable check names and safe GitHub URLs. Browser fixtures cover registration, empty
states, copy/setup, delivery pagination, publication retry, saved reports, disabled
configuration, expired sessions, delayed responses after sign-out and a 390-pixel
mobile layout. Screenshots exercise desktop/mobile setup and delivery cards.
Backend tests cover authenticated pagination, tenant boundaries, invalid pages and
the check-status join. Browser APIs/OAuth and GitHub are fixtures; these checks do
not replace live frontend/backend/PostgreSQL/GitHub end-to-end validation.

Subscription deletion/editing, operator App approval UI, analysis replay, automatic
polling and merge-rule configuration remain outside this phase.
