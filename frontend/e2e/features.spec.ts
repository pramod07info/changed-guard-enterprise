import { expect, test, type Page } from '@playwright/test';
import { enabledFeatures } from './feature-fixture';

async function connect(page: Page, features: unknown, featureStatus = 200) {
  await page.route('**/fixture-authorize?**', async route => {
    const url = new URL(route.request().url());
    await route.fulfill({ status: 302, headers: { location: url.searchParams.get('redirect_uri')! + '?code=fixture-code&state=' + url.searchParams.get('state') } });
  });
  await page.route('**/oauth2/token', route => route.fulfill({ json: { access_token: 'fixture-token', token_type: 'Bearer' } }));
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/auth/config')) return route.fulfill({ json: { authorizationEndpoint: 'http://127.0.0.1:5173/fixture-authorize', tokenEndpoint: 'http://127.0.0.1:5173/oauth2/token', logoutEndpoint: 'http://127.0.0.1:5173/fixture-logout', clientId: 'changeguard-dashboard' } });
    if (path.endsWith('/session')) return route.fulfill({ json: { subject: 'fixture-user', tenantId: 'fixture-team' } });
    if (path.endsWith('/features')) return route.fulfill({ status: featureStatus, json: features });
    if (path.endsWith('/impact/openapi')) return route.fulfill({ json: { service: 'payments', risk: 'HIGH', requiresReview: true, breakingChanges: [], potentialConsumers: [], recommendedTests: [], limitations: [] } });
    if (path.endsWith('/impact/database')) return route.fulfill({ json: { service: 'payments', risk: 'HIGH', requiresReview: true, changes: [], potentialConsumers: [], recommendedTests: [], limitations: [] } });
    return route.fulfill({ status: 500, json: { detail: 'Unexpected feature request: ' + path } });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in', exact: false }).click();
}

test('all disabled capabilities show an empty workspace and send no module requests', async ({ page }) => {
  const requests: string[] = [];
  page.on('request', request => { if (request.url().includes('/api/v1/')) requests.push(new URL(request.url()).pathname); });
  await connect(page, Object.fromEntries(Object.keys(enabledFeatures).map(key => [key, false])));
  await expect(page.getByRole('heading', { name: 'No dashboard features enabled' })).toBeVisible();
  await expect(page.getByText('Database schema changes', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Preview analysis', exact: true })).toHaveCount(0);
  expect([...new Set(requests)].sort()).toEqual(['/api/v1/auth/config', '/api/v1/features', '/api/v1/session'].sort());
});

test('history and enrichment flags leave preview controls and hide save and evidence controls', async ({ page }) => {
  await connect(page, { ...enabledFeatures, catalog: false, baselines: false, githubPr: false, webhooks: false, githubChecks: false, analysisHistory: false, databaseHistory: false, dependencyAnalysis: false, testRecommendations: false });
  await expect(page.getByRole('button', { name: 'Preview analysis', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Analyze & save', exact: true })).toHaveCount(0);
  await expect(page.getByLabel('Service dependencies')).toHaveCount(0);
  await page.getByRole('button', { name: 'Preview analysis', exact: true }).click();
  await expect(page.getByText('Preview complete. This report has not been saved.')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Suggested tests' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Potential impact paths' })).toHaveCount(0);
  await page.getByText('Database schema changes', { exact: true }).click();
  await expect(page.getByRole('button', { name: 'Analyze database & save', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Load database history' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Preview database changes', exact: true }).click();
  await expect(page.getByText('Database preview complete. This report has not been saved.')).toBeVisible();
});

for (const [name, manifest, status] of [['missing', {}, 200], ['malformed', { ...enabledFeatures, catalog: 'true' }, 200], ['expired session', {}, 401]] as const) {
  test(`${name} feature manifest fails closed`, async ({ page }) => {
    await connect(page, manifest, status);
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Preview analysis', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Sign in', exact: false })).toBeVisible();
  });
}
