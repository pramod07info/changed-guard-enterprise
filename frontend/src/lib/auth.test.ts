import { afterEach, expect, test, vi } from 'vitest';
import { challenge, finishSignIn } from './auth';
const pending = { state: 'expected-state', verifier: 'test-proof', redirectUri: 'http://localhost:3000/oauth/callback', createdAt: Date.now() };
function browser(state: string, record: unknown = pending) {
  const values = new Map([['changeguard.oauth.pending', JSON.stringify(record)]]);
  vi.stubGlobal('sessionStorage', { getItem: (key: string) => values.get(key), removeItem: (key: string) => values.delete(key) });
  vi.stubGlobal('window', { location: { pathname: '/oauth/callback', search: '?code=code&state=' + state, origin: 'http://localhost:3000' }, history: { replaceState: vi.fn() } });
  return values;
}
afterEach(() => { vi.unstubAllGlobals(); });
test('PKCE challenge matches RFC 7636 vector', async () => {
  expect(await challenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
});
test('mismatched callback state cannot reach token endpoint', async () => {
  const values = browser('attacker-state');
  const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
  await expect(finishSignIn()).rejects.toThrow('invalid or expired');
  expect(fetch).not.toHaveBeenCalled(); expect(values.size).toBe(0);
});
test('expired callback rejected', async () => {
  browser('expected-state', { ...pending, createdAt: Date.now() - 700000 });
  await expect(finishSignIn()).rejects.toThrow('invalid or expired');
});
test('valid callback exchanges proof and consumes pending session', async () => {
  const values = browser('expected-state');
  const fetch = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ clientId: 'changeguard-dashboard' }) })
    .mockResolvedValueOnce({ ok: true, json: async () => ({ access_token: 'signed-token', token_type: 'Bearer' }) });
  vi.stubGlobal('fetch', fetch);
  expect(await finishSignIn()).toBe('signed-token');
  const body = fetch.mock.calls[1][1].body as URLSearchParams;
  expect(body.get('code_verifier')).toBe('test-proof');
  expect(body.get('client_secret')).toBeNull();
  expect(values.size).toBe(0);
});
