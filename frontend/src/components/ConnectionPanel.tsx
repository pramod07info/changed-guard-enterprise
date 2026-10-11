export function ConnectionPanel({ login, busy }: { login: () => void; busy: boolean }) {
  return <section className="connect card">
    <span className="eyebrow">YOUR TEAM'S CHANGE WORKSPACE</span>
    <h1>Ship with a clearer view<br/>of what changes.</h1>
    <p>Compare API contracts, trace dependent services, and keep an evidence trail for every review.</p>
    <p>Sign in to access your team's workspace.</p>
    <button disabled={busy} onClick={login}>{busy ? 'Signing in…' : 'Sign in →'}</button>
  </section>;
}
