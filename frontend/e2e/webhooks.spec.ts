import { enabledFeatures } from './feature-fixture';
import { expect, test, type Page } from '@playwright/test';
const subId = '11111111-1111-4111-8111-111111111111';
const sub = { id: subId, baselineId: 'baseline-1', repository: 'team/payments', contractPath: 'api/openapi.json', deliveryPath: `/api/v1/github/webhooks/${subId}/deliveries` };
const createdAt = '2026-10-12T08:00:00Z';
const project = { id: 'project-1', name: 'Payments platform', createdBy: 'fixture-user', createdAt };
const service = { id: 'service-1', projectId: project.id, name: 'payments', owner: 'platform-team', repositoryUrl: 'https://github.com/team/payments', createdAt };
const baseline = { id: 'baseline-1', serviceId: service.id, serviceName: 'payments', version: '1.0.0', environment: 'production', publishedBy: 'fixture-user', publishedAt: createdAt, document: { openapi: '3.0.3', paths: {} } };
const report = { service: 'payments', risk: 'HIGH', requiresReview: true, breakingChanges: [{ code: 'RESPONSE_FIELD_REMOVED', operation: 'GET /payments', location: 'response/200/amount', explanation: 'Response field removed' }], potentialConsumers: [], recommendedTests: [], limitations: [] };
const saved = { id: 'fixture-analysis', service: 'payments', createdAt, beforeSnapshotId: 'before', afterSnapshotId: 'after', baselineId: baseline.id, report };
const check = { state: 'FAILED', attempts: 3, checkRunId: null, checkName: 'ChangeGuard / ' + subId, errorCode: 'GITHUB_CHECK_UNAVAILABLE' };
const first = { deliveryId: '22222222-2222-4222-8222-222222222222', state: 'SUCCEEDED', attempts: 1, analysisId: saved.id, errorCode: null, pullNumber: 7, headSha: 'b'.repeat(40), createdAt, check };
const failed = { ...first, deliveryId: '33333333-3333-4333-8333-333333333333', state: 'FAILED', analysisId: null, errorCode: 'INVALID_CONTRACT_OR_BINDING', check: { ...check, state: 'PUBLISHED', attempts: 1, checkRunId: 101, errorCode: null } };

async function fixtures(page: Page, mode: 'normal' | 'disabled' | 'expired' | 'delayed' = 'normal') {
  let registered = false, retried = false, refreshed = false;
  let release: (() => void) | null = null;
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async (text: string) => { (window as unknown as { fixtureCopy: string }).fixtureCopy = text; } } });
  });
  await page.route('**/fixture-authorize?**', async route => {
    const url = new URL(route.request().url());
    await route.fulfill({ status: 302, headers: { location: url.searchParams.get('redirect_uri')! + '?code=fixture-code&state=' + url.searchParams.get('state') } });
  });
  await page.route('**/oauth2/token', route => route.fulfill({ status: 200, contentType: 'application/json', body: '{"access_token":"fixture-token","token_type":"Bearer"}' }));
  await page.route('**/fixture-logout', route => route.fulfill({ status: 302, headers: { location: '/' } }));
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname, method = route.request().method();
    if (path === '/api/v1/features') { await route.fulfill({ json: enabledFeatures }); return; }
    let body: unknown;
    if (path.endsWith('/auth/config')) body = { authorizationEndpoint: 'http://127.0.0.1:5173/fixture-authorize', tokenEndpoint: 'http://127.0.0.1:5173/oauth2/token', logoutEndpoint: 'http://127.0.0.1:5173/fixture-logout', clientId: 'changeguard-dashboard' };
    else {
      expect(route.request().headers().authorization).toBe('Bearer fixture-token');
      if (path.endsWith('/session')) body = { subject: 'fixture-user', tenantId: 'team-platform' };
      else if (path.endsWith('/projects')) body = [project];
      else if (path.endsWith('/services')) body = [service];
      else if (path.endsWith('/baselines')) body = [baseline];
      else if (path.endsWith('/baseline-1')) body = baseline;
      else if (path.endsWith('/github-webhooks')) {
        if (method === 'POST') {
          if (mode === 'disabled') { await route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{"detail":"GitHub webhooks are disabled"}' }); return; }
          const input = route.request().postDataJSON();
          expect(Object.keys(input).sort()).toEqual(['contractPath', 'dependencies']);
          expect(input.contractPath).toBe('api/openapi.json'); registered = true; body = sub;
        } else body = registered || mode === 'expired' || mode === 'delayed' ? [sub] : [];
      } else if (path.endsWith('/deliveries')) {
        if (mode === 'expired') { await route.fulfill({ status: 401, body: '' }); return; }
        if (mode === 'delayed') await new Promise<void>(resolve => { release = resolve; });
        const offset = Number(url.searchParams.get('offset') ?? 0);
        if (retried) refreshed = true;
        const current = { ...first, check: retried ? { ...check, state: refreshed ? 'PUBLISHED' : 'QUEUED', attempts: 1, checkRunId: refreshed ? 99 : null, errorCode: null } : check };
        body = offset === 0 ? [current, failed, ...Array.from({ length: 18 }, (_, i) => ({ ...first, deliveryId: `44444444-4444-4444-8444-${String(i).padStart(12, '0')}`, check: null, analysisId: null, state: 'QUEUED' }))] : [failed];
      } else if (path.endsWith('/check/retry')) {
        expect(method).toBe('POST'); retried = true; body = { ...check, state: 'QUEUED', attempts: 0, errorCode: null };
      } else if (path.endsWith('/fixture-analysis')) body = saved;
      else { await route.fulfill({ status: 404, body: '' }); return; }
    }
    await route.fulfill({ status: method === 'POST' ? 201 : 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  return { release: () => { release?.(); }, waiting: () => release !== null };
}
async function selectBaseline(page: Page) {
  await page.goto('/'); await page.getByRole('button', { name: 'Sign in', exact: false }).click();
  await page.getByText('Project catalog & contract baselines', { exact: true }).click();
  await page.getByRole('button', { name: 'Load projects', exact: true }).click();
  await page.getByLabel('Project', { exact: true }).selectOption(project.id);
  await page.getByLabel('Registered service', { exact: true }).selectOption(service.id);
  await page.getByLabel('Published baseline', { exact: true }).selectOption(baseline.id);
  await page.getByText('Webhook automation', { exact: true }).click();
}

test('register, setup URL, deliveries, publication retry, saved analysis and mobile', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await fixtures(page); await selectBaseline(page);
  await page.getByRole('button', { name: 'Load subscriptions', exact: true }).click();
  await expect(page.getByText('No webhook subscriptions for this baseline yet.')).toBeVisible();
  await page.getByLabel('Webhook contract file path').fill('api/openapi.json');
  await page.getByLabel('Webhook dependency map').fill('{"orders":["payments"]}');
  await page.getByRole('button', { name: 'Register webhook subscription', exact: true }).click();
  await expect(page.locator('.webhook-delivery')).toHaveCount(20);
  await page.getByLabel('Public HTTPS origin').fill('http://localhost:8080');
  await expect(page.getByRole('button', { name: 'Copy webhook URL' })).toBeDisabled();
  await page.getByLabel('Public HTTPS origin').fill('https://changeguard.example.com');
  await expect(page.getByLabel('GitHub webhook URL')).toHaveValue('https://changeguard.example.com' + sub.deliveryPath);
  await page.getByRole('button', { name: 'Copy webhook URL' }).click();
  await expect(page.locator('.webhook-panel [role=status]')).toContainText('Webhook URL copied');
  expect(await page.evaluate(() => (window as unknown as { fixtureCopy: string }).fixtureCopy)).toBe('https://changeguard.example.com' + sub.deliveryPath);
  await expect(page.getByLabel('Required check name')).toHaveValue(check.checkName);
  const row = page.getByRole('article', { name: 'Delivery ' + first.deliveryId, exact: true });
  await expect(row).toContainText('Publication failed');
  await row.getByRole('button', { name: 'Retry check publication' }).click();
  await expect(row).toContainText('Check queued'); await expect(row.getByRole('button', { name: 'Retry check publication' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Refresh deliveries' }).click();
  await expect(row).toContainText('Result published'); await expect(row).toContainText('see whether it passed');
  await row.getByRole('button', { name: 'Open saved analysis' }).click();
  await expect(page.getByText('Saved webhook analysis loaded.', { exact: true })).toBeVisible();
  await expect(page.locator('.table-wrap tbody')).toContainText('Response field removed');
  await page.locator('.webhook-setup').screenshot({ path: 'test-results/webhook-setup-desktop.png' });
  await row.screenshot({ path: 'test-results/webhook-delivery-desktop.png' });
  await page.getByRole('button', { name: 'Next deliveries' }).click(); await expect(page.locator('.webhook-delivery')).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Next deliveries' })).toBeDisabled();
  await page.getByRole('button', { name: 'Previous deliveries' }).click(); await expect(page.locator('.webhook-delivery')).toHaveCount(20);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('.webhook-setup').screenshot({ path: 'test-results/webhook-setup-mobile.png' });
  await row.screenshot({ path: 'test-results/webhook-delivery-mobile.png' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByLabel('Required check name')).toHaveCount(0); expect(errors).toEqual([]);
});

test('disabled registration and invalid input have clear feedback', async ({ page }) => {
  await fixtures(page, 'disabled'); await selectBaseline(page);
  await page.getByLabel('Webhook contract file path').fill('../openapi.json');
  await page.getByRole('button', { name: 'Register webhook subscription' }).click();
  await expect(page.getByRole('alert')).toContainText('relative .json path');
  await page.getByLabel('Webhook contract file path').fill('api/openapi.json');
  await page.getByRole('button', { name: 'Register webhook subscription' }).click();
  await expect(page.getByRole('alert')).toContainText('GitHub webhooks are disabled');
});

test('expired delivery request clears the workspace', async ({ page }) => {
  await fixtures(page, 'expired'); await selectBaseline(page);
  await page.getByRole('button', { name: 'Load subscriptions' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: false })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('invalid or expired');
  await expect(page.getByLabel('Webhook subscription')).toHaveCount(0);
});

test('late delivery response cannot restore a signed-out workspace', async ({ page }) => {
  const control = await fixtures(page, 'delayed'); await selectBaseline(page);
  await page.getByRole('button', { name: 'Load subscriptions' }).click();
  await expect.poll(control.waiting).toBe(true);
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: false })).toBeVisible();
  control.release();
  await expect(page.getByLabel('Webhook subscription')).toHaveCount(0);
  await expect(page.locator('.webhook-delivery')).toHaveCount(0);
});

test('checks and dependencies disabled keep webhook analysis available', async ({ page }) => {
  await fixtures(page);
  await page.route('**/api/v1/features', route => route.fulfill({ json: { ...enabledFeatures, githubChecks: false, dependencyAnalysis: false } }));
  await selectBaseline(page);
  await expect(page.getByLabel('Webhook dependency map')).toHaveCount(0);
  await page.getByLabel('Webhook contract file path').fill('api/openapi.json');
  await page.getByRole('button', { name: 'Register webhook subscription', exact: true }).click();
  await expect(page.getByText('Analysis saved', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Required check name')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Retry check publication' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Open GitHub check ↗' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Open saved analysis', exact: true }).click();
  await expect(page.getByText('Saved webhook analysis loaded.')).toBeVisible();
});

test('catalog metadata works without requesting disabled baselines', async ({ page }) => {
  const baselineRequests: string[] = [];
  page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/baselines')) baselineRequests.push(request.url()); });
  await fixtures(page);
  await page.route('**/api/v1/features', route => route.fulfill({ json: { ...enabledFeatures, baselines: false, githubPr: false, webhooks: false, githubChecks: false } }));
  await page.goto('/'); await page.getByRole('button', { name: 'Sign in', exact: false }).click();
  await page.getByText('Project catalog & contract baselines', { exact: true }).click();
  await page.getByRole('button', { name: 'Load projects', exact: true }).click();
  await page.getByLabel('Project', { exact: true }).selectOption(project.id);
  await page.getByLabel('Registered service', { exact: true }).selectOption(service.id);
  await expect(page.getByText('Owner: platform-team', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Publish baseline', exact: true })).toHaveCount(0);
  expect(baselineRequests).toEqual([]);
});
