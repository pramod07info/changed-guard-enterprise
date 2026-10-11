import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';

afterEach(() => vi.unstubAllGlobals());
describe('API transport', () => {
  it('sends bearer identity and serializes POST input without a tenant selector', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{"id":"saved"}', { status: 201 }));
    vi.stubGlobal('fetch', fetch);
    expect(await api('/analyses', 'token', { service: 'payments' })).toEqual({ id: 'saved' });
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer token');
    expect(fetch.mock.calls[0][1].body).toBe('{"service":"payments"}');
  });
  it('handles empty authentication errors and structured validation messages', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
    await expect(api('/session', 'expired')).rejects.toMatchObject({ status: 401 });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"detail":"Unsupported schema"}', { status: 400 })));
    await expect(api('/analyses', 'token', {})).rejects.toThrow('Unsupported schema');
  });
});
