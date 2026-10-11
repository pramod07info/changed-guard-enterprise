import { expect, test, type Page } from '@playwright/test';

const report = {
  service: 'payments', risk: 'HIGH', requiresReview: true,
  breakingChanges: [{ code: 'RESPONSE_FIELD_REMOVED', operation: 'GET /payments', location: 'response/200/amount',
    explanation: 'Previously documented response field is removed' }],
  potentialConsumers: [{ service: 'orders', distance: 1, path: ['payments', 'orders'] }],
  recommendedTests: ['Run consumer contract tests: orders -> payments'],
  limitations: ['Bounded schema subset; human review is required.']
};
const saved = { id: 'fixture-analysis', service: 'payments', createdAt: '2026-10-11T10:00:00Z',
  beforeSnapshotId: 'fixture-before', afterSnapshotId: 'fixture-after', report };

async function oauthFixture(page: Page) {
  await page.route('**/fixture-authorize?**', async route => {
    const url = new URL(route.request().url());
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    expect(url.searchParams.get('code_challenge')).toBeTruthy();
    await route.fulfill({ status: 302, headers: { location: url.searchParams.get('redirect_uri')! + '?code=fixture-code&state=' + url.searchParams.get('state') } });
  });
  await page.route('**/oauth2/token', async route => {
    const body = new URLSearchParams(route.request().postData()!);
    expect(body.get('grant_type')).toBe('authorization_code');
    expect(body.get('code_verifier')!.length).toBeGreaterThanOrEqual(43);
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{"access_token":"fixture-token","token_type":"Bearer"}' });
  });
  await page.route('**/fixture-logout', route => route.fulfill({ status: 302, headers: { location: '/' } }));
}
const authConfig = { authorizationEndpoint: 'http://127.0.0.1:5173/fixture-authorize',
  tokenEndpoint: 'http://127.0.0.1:5173/oauth2/token', logoutEndpoint: 'http://127.0.0.1:5173/fixture-logout', clientId: 'changeguard-dashboard' };

test('connect, analyze/save, history, snapshot, mobile and logout', async ({ page }) => {
  await oauthFixture(page);
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    const body = path.endsWith('/auth/config') ? authConfig : path.endsWith('/session') ? { subject: 'fixture-user', tenantId: 'team-platform' } :
      path.includes('/snapshots/') ? { id: 'fixture-before', service: 'payments', createdAt: saved.createdAt, document: { openapi: '3.0.3', paths: {} } } :
      path.endsWith('/analyses') && route.request().method() === 'GET' ?
        [{ id: saved.id, service: 'payments', createdAt: saved.createdAt, risk: 'HIGH', requiresReview: true }] :
      path.endsWith('/impact/openapi') ? report : saved;
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in', exact: false }).click();
  await page.getByRole('button', { name: 'Preview analysis', exact: true }).click();
  await expect(page.getByText('Preview complete. This report has not been saved.')).toBeVisible();
  await page.getByRole('button', { name: 'Analyze & save', exact: true }).click();
  await expect(page.getByText('Analysis and contract snapshots saved.')).toBeVisible();
  await expect(page.getByText('Previously documented response field is removed')).toBeVisible();
  await page.getByRole('button', { name: 'Load history' }).click();
  await expect(page.locator('.history-row')).toHaveCount(1);
  await page.locator('.history-row').click();
  await expect(page.getByText('Saved analysis loaded.')).toBeVisible();
  await page.getByRole('button', { name: 'Before snapshot' }).click();
  await expect(page.getByRole('heading', { name: 'Contract snapshot' })).toBeVisible();
  await page.getByRole('button', { name: 'Close', exact: true }).click();
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: false })).toBeVisible();
  await expect(page.getByText('Previously documented response field is removed')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('expired access token disconnects and presents a clear error', async ({ page }) => {
  await oauthFixture(page);
  await page.route('**/api/v1/**', async route => {
    if (new URL(route.request().url()).pathname.endsWith('/auth/config'))
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(authConfig) });
    else if (new URL(route.request().url()).pathname.endsWith('/session'))
      await route.fulfill({ status: 200, contentType: 'application/json', body: '{"subject":"fixture-user","tenantId":"tenant-a"}' });
    else await route.fulfill({ status: 401, body: '' });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in', exact: false }).click();
  await page.getByRole('button', { name: 'Analyze & save' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: false })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('invalid or expired');
});
