import { expect, test } from 'vitest';
import { checkName, githubCheckUrl, parseWebhookRequest, webhookUrl } from './webhooks';
const id = '11111111-1111-4111-8111-111111111111';
const subscription = { id, baselineId: 'baseline', repository: 'team/payments', contractPath: 'api/openapi.json', deliveryPath: `/api/v1/github/webhooks/${id}/deliveries` };

test('registration sends only a validated contract path and dependency map', () => {
  expect(parseWebhookRequest('api/openapi.json', '{"orders":["payments"]}')).toEqual({ contractPath: 'api/openapi.json', dependencies: { orders: ['payments'] } });
  expect(() => parseWebhookRequest('../api.json', '{}')).toThrow('relative .json');
  expect(() => parseWebhookRequest('api.json', '{"orders":"payments"}')).toThrow('provider name lists');
});
test('delivery URL uses an explicit HTTPS origin and the exact subscription path', () => {
  expect(webhookUrl('https://changeguard.example.com/', subscription)).toBe('https://changeguard.example.com' + subscription.deliveryPath);
  expect(() => webhookUrl('https://changeguard.example.com', { ...subscription, deliveryPath: '//other.example/deliveries' })).toThrow('delivery path');
});
test('origins cannot carry credentials, paths, query parameters or fragments', () => {
  for (const origin of ['http://localhost:8080', 'https://user:password@example.com', 'https://example.com/api', 'https://example.com?secret=x', 'https://example.com#key', 'not-a-url'])
    expect(() => webhookUrl(origin, subscription)).toThrow();
});
test('check name is stable for the approved subscription', () => {
  expect(checkName(id)).toBe('ChangeGuard / ' + id);
});
test('GitHub links reject arbitrary origins, traversal and unsafe numeric IDs', () => {
  expect(githubCheckUrl('team/payments', 99)).toBe('https://github.com/team/payments/runs/99');
  for (const repo of ['https://evil.example', '../payments', 'team/..', 'team/payments?token=x']) expect(githubCheckUrl(repo, 99)).toBeNull();
  for (const number of [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1]) expect(githubCheckUrl('team/payments', number)).toBeNull();
});
