import { validateContractPath } from './github';
import { parseDependencies } from './input';
import type { WebhookSubscription } from '../types';

export function parseWebhookRequest(contractPath: string, dependencies: string) {
  return { contractPath: validateContractPath(contractPath), dependencies: parseDependencies(dependencies) };
}
export function webhookUrl(origin: string, subscription: WebhookSubscription): string {
  let url: URL;
  try { url = new URL(origin.trim()); } catch { throw new Error('Enter the public HTTPS origin, such as https://changeguard.example.com.'); }
  if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash || (url.pathname !== '/' && url.pathname !== ''))
    throw new Error('Use an HTTPS origin without credentials, a path, query or fragment.');
  const expected = `/api/v1/github/webhooks/${subscription.id}/deliveries`;
  if (subscription.deliveryPath !== expected) throw new Error('The subscription delivery path is invalid. Reload subscriptions.');
  return url.origin + expected;
}
export function checkName(subscriptionId: string) { return 'ChangeGuard / ' + subscriptionId; }
export function githubCheckUrl(repository: string, id: number): string | null {
  const parts = repository.split('/');
  if (parts.length !== 2 || parts.some(part => !/^[A-Za-z0-9_.-]+$/.test(part) || part === '.' || part === '..') || !Number.isSafeInteger(id) || id < 1) return null;
  return `https://github.com/${parts.map(encodeURIComponent).join('/')}/runs/${id}`;
}
