const pendingKey = 'changeguard.oauth.pending';
const maxAge = 10 * 60 * 1000;
type Configuration = { authorizationEndpoint: string; tokenEndpoint: string; logoutEndpoint: string; clientId: string };
type Pending = { state: string; verifier: string; redirectUri: string; createdAt: number };
async function configuration(): Promise<Configuration> {
  const response = await fetch('/api/v1/auth/config', { signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error('Unable to load sign-in configuration.');
  return await response.json() as Configuration;
}
function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
export async function challenge(verifier: string): Promise<string> {
  return base64url(new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))));
}
export async function signIn(): Promise<void> {
  const config = await configuration();
  const verifier = base64url(crypto.getRandomValues(new Uint8Array(32)));
  const state = base64url(crypto.getRandomValues(new Uint8Array(32)));
  const redirectUri = window.location.origin + '/oauth/callback';
  const pending: Pending = { state, verifier, redirectUri, createdAt: Date.now() };
  const url = new URL(config.authorizationEndpoint);
  url.search = new URLSearchParams({ response_type: 'code', client_id: config.clientId,
    redirect_uri: redirectUri, scope: 'changeguard-api', state,
    code_challenge: await challenge(verifier), code_challenge_method: 'S256' }).toString();
  sessionStorage.setItem(pendingKey, JSON.stringify(pending));
  window.location.assign(url.toString());
}
export async function finishSignIn(): Promise<string | null> {
  if (window.location.pathname !== '/oauth/callback') return null;
  const params = new URLSearchParams(window.location.search);
  const raw = sessionStorage.getItem(pendingKey);
  sessionStorage.removeItem(pendingKey);
  window.history.replaceState({}, '', '/');
  if (!raw) throw new Error('Sign-in session is missing. Please sign in again.');
  const pending = JSON.parse(raw) as Pending;
  if (params.get('state') !== pending.state || !pending.state || !pending.verifier ||
      pending.redirectUri !== window.location.origin + '/oauth/callback' ||
      !Number.isFinite(pending.createdAt) || Date.now() - pending.createdAt > maxAge || pending.createdAt > Date.now()) {
    throw new Error('Sign-in session is invalid or expired. Please sign in again.');
  }
  if (params.has('error') || !params.get('code')) throw new Error('Sign-in was cancelled or denied.');
  const config = await configuration();
  const response = await fetch('/oauth2/token', {
    method: 'POST', credentials: 'omit', signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'authorization_code', client_id: config.clientId,
      code: params.get('code')!, redirect_uri: pending.redirectUri, code_verifier: pending.verifier })
  });
  if (!response.ok) throw new Error('Unable to complete sign-in. Please try again.');
  const result = await response.json() as { access_token?: string; token_type?: string };
  if (!result.access_token || result.token_type?.toLowerCase() !== 'bearer') throw new Error('Invalid sign-in response.');
  return result.access_token;
}
export async function signOut(): Promise<void> {
  const config = await configuration();
  window.location.assign(config.logoutEndpoint);
}
