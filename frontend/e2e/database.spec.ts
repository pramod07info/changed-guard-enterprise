import { expect, test, type Page } from '@playwright/test';
const report = { service: 'payments', risk: 'HIGH', requiresReview: true, changes: [{ code: 'COLUMN_REMOVED', table: 'public.payments', location: 'columns/reference', explanation: 'Existing queries can depend on this column', severity: 'HIGH' }], potentialConsumers: [{ service: 'orders', distance: 1, path: ['payments', 'orders'] }], recommendedTests: ['Run migration validation on a disposable database'], limitations: ['Normalized metadata only; SQL is not executed'] };
const before = { dialect: 'postgresql', tables: { 'public.payments': { columns: { id: { type: 'uuid', nullable: false }, reference: { type: 'text', nullable: true } }, primaryKey: ['id'] } } };
const after = { dialect: 'postgresql', tables: { 'public.payments': { columns: { id: { type: 'uuid', nullable: false } }, primaryKey: ['id'] } } };
const saved = { id: 'schema-fixture', service: 'payments', createdAt: '2026-10-12T09:00:00Z', report, before, after, dependencies: { orders: ['payments'] } };
async function fixture(page: Page, mode: 'normal' | 'unsupported' | 'expired' = 'normal') {
  await page.route('**/fixture-authorize?**', async route => {
    const url = new URL(route.request().url()); await route.fulfill({ status: 302, headers: { location: url.searchParams.get('redirect_uri')! + '?code=fixture&state=' + url.searchParams.get('state') } });
  });
  await page.route('**/oauth2/token', route => route.fulfill({ status: 200, contentType: 'application/json', body: '{"access_token":"fixture-token","token_type":"Bearer"}' }));
  await page.route('**/fixture-logout', route => route.fulfill({ status: 302, headers: { location: '/' } }));
  let saves = 0;
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname, method = route.request().method(); let body: unknown;
    if (path.endsWith('/auth/config')) body = { authorizationEndpoint: 'http://127.0.0.1:5173/fixture-authorize', tokenEndpoint: 'http://127.0.0.1:5173/oauth2/token', logoutEndpoint: 'http://127.0.0.1:5173/fixture-logout', clientId: 'changeguard-dashboard' };
    else if (path.endsWith('/session')) body = { subject: 'fixture-user', tenantId: 'team-platform' };
    else {
      expect(route.request().headers().authorization).toBe('Bearer fixture-token');
      if (mode === 'expired') { await route.fulfill({ status: 401, body: '' }); return; }
      if (method === 'POST' && mode === 'unsupported') { await route.fulfill({ status: 400, contentType: 'application/problem+json', body: '{"detail":"Unsupported column type"}' }); return; }
      if (path.endsWith('/impact/database') || (path.endsWith('/database-analyses') && method === 'POST')) {
        const input = route.request().postDataJSON(); expect(Object.keys(input).sort()).toEqual(['after', 'before', 'dependencies', 'service']); expect(input.before.dialect).toBe('postgresql');
        if (path.endsWith('/database-analyses')) { saves++; body = { ...saved, before: input.before, after: input.after, dependencies: input.dependencies }; }
        else body = report;
      } else if (path.endsWith('/database-analyses')) body = saves ? [{ id: saved.id, service: 'payments', createdAt: saved.createdAt, risk: 'HIGH', requiresReview: true }] : [];
      else if (path.endsWith('/schema-fixture')) body = saved;
      else { await route.fulfill({ status: 404, body: '' }); return; }
    }
    await route.fulfill({ status: method === 'POST' && path.endsWith('/database-analyses') ? 201 : 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto('/'); await page.getByRole('button', { name: 'Sign in', exact: false }).click();
  await page.getByText('Database schema changes', { exact: true }).click();
}
test('database preview, save, history and evidence work on mobile without changing API reports', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', error => errors.push(error.message));
  await fixture(page);
  await page.getByRole('button', { name: 'Load database history' }).click();
  await expect(page.getByText('No database analyses on this page.')).toBeVisible();
  await page.getByRole('button', { name: 'Preview database changes' }).click();
  await expect(page.locator('.database-panel [role=status]')).toContainText('has not been saved');
  await expect(page.locator('.database-report')).toContainText('column removed');
  await page.getByRole('button', { name: 'Analyze database & save' }).click();
  await expect(page.locator('.database-panel [role=status]')).toContainText('evidence saved');
  await page.getByRole('button', { name: 'View saved schema evidence' }).click();
  await expect(page.getByRole('heading', { name: 'Saved before schema', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Hide saved schema evidence' }).click();
  await page.getByRole('button', { name: 'Load database history' }).click();
  await page.locator('.database-history-row').click();
  await expect(page.locator('.database-panel [role=status]')).toContainText('Saved database analysis loaded');
  await expect(page.getByLabel('Before schema', { exact: false })).toHaveValue(JSON.stringify(before, null, 2));
  await page.locator('.database-report').screenshot({ path: 'test-results/database-desktop.png' });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('.database-report').screenshot({ path: 'test-results/database-mobile.png' });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect(page.getByRole('heading', { name: 'Every change has a story.' })).toBeVisible();
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.locator('.database-report')).toHaveCount(0); expect(errors).toEqual([]);
});
test('database input validation and server unsupported-feature errors are explicit', async ({ page }) => {
  await fixture(page, 'unsupported');
  await page.getByLabel('Before schema', { exact: false }).fill('ALTER TABLE payments DROP COLUMN amount');
  await page.getByRole('button', { name: 'Preview database changes' }).click();
  await expect(page.getByRole('alert')).toContainText('Before schema must be valid JSON');
  await page.getByRole('button', { name: 'Load database example' }).click();
  await page.getByRole('button', { name: 'Analyze database & save' }).click();
  await expect(page.getByRole('alert')).toContainText('Unsupported column type');
});
test('expired database request clears the session and database views', async ({ page }) => {
  await fixture(page, 'expired'); await page.getByRole('button', { name: 'Preview database changes' }).click();
  await expect(page.getByRole('alert')).toContainText('invalid or expired');
  await expect(page.getByRole('button', { name: 'Sign in', exact: false })).toBeVisible();
  await expect(page.getByLabel('Database service', { exact: true })).toHaveCount(0);
});
