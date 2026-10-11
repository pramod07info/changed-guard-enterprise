import { useState } from 'react';

export function ConnectionPanel({ connect, busy }: { connect: (token: string) => void; busy: boolean }) {
  const [token, setToken] = useState('');
  return <section className="connect card">
    <span className="eyebrow">YOUR TEAM'S CHANGE WORKSPACE</span>
    <h1>Ship with a clearer view<br/>of what changes.</h1>
    <p>Compare API contracts, trace dependent services, and keep an evidence trail for every review.</p>
    <form onSubmit={e => { e.preventDefault(); connect(token.trim()); }}>
      <label htmlFor="token">Identity provider access token</label>
      <input id="token" type="password" autoComplete="off" value={token} onChange={e => setToken(e.target.value)} required placeholder="Paste your access token"/>
      <small>The token stays in memory and is cleared when you disconnect or refresh.</small>
      <button disabled={busy || !token.trim()} type="submit">{busy ? 'Verifying identity…' : 'Connect workspace →'}</button>
    </form>
  </section>;
}
