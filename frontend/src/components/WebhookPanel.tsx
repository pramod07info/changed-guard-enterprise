import type { Features } from '../lib/features';
import { useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';
import { checkName, githubCheckUrl, parseWebhookRequest, webhookUrl } from '../lib/webhooks';
import type { Baseline, CheckPublication, SavedAnalysis, WebhookDelivery, WebhookSubscription } from '../types';

type Props = { features: Features;
  baseline: Baseline; repositoryUrl: string | null; dependencies: string; token: string; busy: boolean;
  fail: (message: string) => void;
  execute: <T>(task: () => Promise<T>, done: (value: T) => void) => Promise<void>;
  loaded: (result: SavedAnalysis) => void;
};
const pageSize = 20;
const analysisLabels = { QUEUED: 'Analysis queued', PROCESSING: 'Analyzing', SUCCEEDED: 'Analysis saved', SUPERSEDED: 'Newer commit detected', FAILED: 'Analysis failed' };
const checkLabels = { QUEUED: 'Check queued', PROCESSING: 'Publishing check', PUBLISHED: 'Result published', FAILED: 'Publication failed' };

export function WebhookPanel({ features, baseline, repositoryUrl, dependencies, token, busy, fail, execute, loaded }: Props) {
  const [subscriptions, setSubscriptions] = useState<WebhookSubscription[]>([]);
  const [subscriptionsLoaded, setSubscriptionsLoaded] = useState(false);
  const [selected, setSelected] = useState('');
  const [path, setPath] = useState('openapi.json');
  const [graph, setGraph] = useState(dependencies);
  const [origin, setOrigin] = useState('');
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [deliveriesLoaded, setDeliveriesLoaded] = useState(false);
  const [offset, setOffset] = useState(0);
  const [message, setMessage] = useState('');
  const epoch = useRef(0);
  useEffect(() => () => { epoch.current++; }, []);
  const subscription = subscriptions.find(item => item.id === selected) ?? null;
  let supportedRepository = false;
  try { supportedRepository = new URL(repositoryUrl ?? '').hostname === 'github.com'; } catch { /* No registered GitHub URL. */ }
  let deliveryUrl = '', originError = '';
  if (subscription && origin.trim()) {
    try { deliveryUrl = webhookUrl(origin, subscription); } catch (error) { originError = (error as Error).message; }
  }
  function run<T>(task: () => Promise<T>, done: (value: T) => void) {
    const current = epoch.current;
    return execute(async () => {
      try { return { value: await task() }; }
      catch (error) { if (epoch.current !== current) return null; throw error; }
    }, result => { if (result && epoch.current === current) done(result.value); });
  }
  function loadDeliveries(id: string, page: number) {
    void run(() => api<WebhookDelivery[]>(`/github/webhooks/${id}/deliveries?limit=${pageSize}&offset=${page}`, token), result => {
      setDeliveries(result); setOffset(page); setDeliveriesLoaded(true);
    });
  }
  function select(id: string) {
    epoch.current++; setSelected(id); setDeliveries([]); setOffset(0); setDeliveriesLoaded(false); setMessage('');
    if (id) loadDeliveries(id, 0);
  }
  function loadSubscriptions() {
    epoch.current++; setMessage('');
    void run(() => api<WebhookSubscription[]>(`/baselines/${baseline.id}/github-webhooks`, token), result => {
      setSubscriptions(result); setSubscriptionsLoaded(true);
      select(result.some(item => item.id === selected) ? selected : result[0]?.id ?? '');
    });
  }
  function register() {
    try {
      const body = parseWebhookRequest(path, features.dependencyAnalysis ? graph : '{}');
      setMessage('');
      void run(() => api<WebhookSubscription>(`/baselines/${baseline.id}/github-webhooks`, token, body), result => {
        setSubscriptions(current => [result, ...current.filter(item => item.id !== result.id)]);
        setSubscriptionsLoaded(true); select(result.id);
        setMessage('Subscription registered. Configure its webhook in GitHub to start receiving deliveries.');
      });
    } catch (error) { fail(error instanceof Error ? error.message : 'Invalid webhook input.'); }
  }
  async function copy(text: string, label: string) {
    const current = epoch.current;
    try {
      await navigator.clipboard.writeText(text);
      if (current === epoch.current) setMessage(label + ' copied.');
    } catch { if (current === epoch.current) setMessage('Copy is unavailable. Select and copy the read-only field instead.'); }
  }
  function retry(row: WebhookDelivery) {
    if (!subscription || row.check?.state !== 'FAILED') return;
    setMessage('');
    void run(() => api<CheckPublication>(`/github/webhooks/${subscription.id}/deliveries/${row.deliveryId}/check/retry`, token, {}), result => {
      setDeliveries(current => current.map(item => item.deliveryId === row.deliveryId ? { ...item, check: result } : item));
      setMessage('Check publication queued again. The saved analysis has not been rerun. Refresh deliveries to see progress.');
    });
  }
  return <details className="webhook-panel"><summary>Webhook automation</summary>
    <h3>Connect this baseline to GitHub events</h3>
    <p>Register a contract path for {baseline.serviceName} · {baseline.environment} · {baseline.version}. Each delivery compares against this immutable baseline.</p>
    {!supportedRepository && <p className="alert">Set this service’s repository to an HTTPS GitHub repository before registering a webhook.</p>}
    <form onSubmit={event => { event.preventDefault(); register(); }}>
      <label htmlFor="webhook-contract-path">Webhook contract file path</label>
      <input id="webhook-contract-path" maxLength={500} required disabled={busy} value={path} onChange={event => setPath(event.target.value)}/>
      {features.dependencyAnalysis && <><label htmlFor="webhook-dependencies">Webhook dependency map</label>
      <textarea id="webhook-dependencies" className="graph-input" spellCheck={false} disabled={busy} value={graph} onChange={event => setGraph(event.target.value)}/>
      <small>This map is saved with the subscription. Later edits to comparison inputs do not change it.</small></>}
      <button disabled={busy || !supportedRepository}>Register webhook subscription</button>
    </form>
    <div className="actions"><button className="secondary" disabled={busy} onClick={loadSubscriptions}>Load subscriptions</button></div>
    {subscriptionsLoaded && !subscriptions.length && <p>No webhook subscriptions for this baseline yet.</p>}
    {!subscriptionsLoaded && <p className="subtle">Load existing subscriptions or register a new one. Up to 100 subscriptions are shown.</p>}
    {!!subscriptions.length && <><label htmlFor="webhook-subscription">Webhook subscription</label>
      <select id="webhook-subscription" disabled={busy} value={selected} onChange={event => select(event.target.value)}>
        <option value="">Select a subscription</option>{subscriptions.map(item => <option key={item.id} value={item.id}>{item.contractPath} · {item.id}</option>)}
      </select></>}
    {message && <div className="notice" role="status">{message}</div>}
    {subscription && <>
      <div className="webhook-setup">
        <h3>Webhook setup</h3>
        <p>Repository: <strong>{subscription.repository}</strong></p>
        <label htmlFor="webhook-delivery-path">Delivery path</label><input id="webhook-delivery-path" readOnly value={subscription.deliveryPath}/>
        <label htmlFor="webhook-public-origin">Public HTTPS origin</label><input id="webhook-public-origin" type="url" placeholder="https://changeguard.example.com" value={origin} onChange={event => { setOrigin(event.target.value); setMessage(''); }}/>
        <small>Use the externally reachable origin that routes this API path to your backend. A localhost URL cannot receive GitHub deliveries.</small>
        {originError && <p role="alert" className="alert">{originError}</p>}
        <label htmlFor="webhook-delivery-url">GitHub webhook URL</label><input id="webhook-delivery-url" readOnly value={deliveryUrl} placeholder="Enter a public HTTPS origin above"/>
        <div className="actions"><button className="quiet" disabled={!deliveryUrl} onClick={() => void copy(deliveryUrl, 'Webhook URL')}>Copy webhook URL</button></div>
        {features.githubChecks && <><label htmlFor="webhook-check-name">Required check name</label><input id="webhook-check-name" readOnly value={checkName(subscription.id)}/>
        <div className="actions"><button className="quiet" onClick={() => void copy(checkName(subscription.id), 'Check name')}>Copy check name</button></div></>}
        <ol className="webhook-instructions">
          <li>In GitHub repository settings, add this webhook URL, choose JSON content and subscribe to Pull requests.</li>
          <li>Your backend operator configures the webhook secret{features.githubChecks ? ", GitHub App key and subscription approval" : ""}. Credentials are never entered here.</li>
          {features.githubChecks && <li>After a real check appears, require this exact check name from your expected GitHub App in the repository’s merge rules.</li>}
        </ol>
        <p className="subtle">Registration does not configure GitHub, approve App writes or enable merge blocking. Those settings are completed separately.</p>
      </div>
      <div className="section-heading webhook-heading"><div><h3>Recent webhook deliveries</h3><p className="subtle">Refresh to see analysis and publication progress.</p></div>
        <button className="secondary" disabled={busy} onClick={() => loadDeliveries(subscription.id, offset)}>Refresh deliveries</button></div>
      {deliveriesLoaded && !deliveries.length && <p>No deliveries on this page. Open or update a PR after configuring the webhook.</p>}
      <div className="webhook-deliveries">{deliveries.map(row => {
        const checkUrl = row.check?.checkRunId ? githubCheckUrl(subscription.repository, row.check.checkRunId) : null;
        return <article className="webhook-delivery" key={row.deliveryId} aria-label={`Delivery ${row.deliveryId}`}>
          <div className="section-heading"><h4>PR #{row.pullNumber}</h4><time dateTime={row.createdAt}>{new Date(row.createdAt).toLocaleString()}</time></div>
          <dl><dt>Delivery</dt><dd><code>{row.deliveryId}</code></dd><dt>Commit</dt><dd><code>{row.headSha}</code></dd>
            <dt>Analysis</dt><dd><span className="webhook-state">{analysisLabels[row.state]}</span> · Attempt {row.attempts}{row.errorCode && <small>Error: {row.errorCode}</small>}</dd>
            {features.githubChecks && <><dt>GitHub check</dt><dd>{row.check ? <><span className="webhook-state">{checkLabels[row.check.state]}</span> · Attempt {row.check.attempts}{row.check.errorCode && <small>Error: {row.check.errorCode}</small>}</> : 'Not queued. Check publication needs App approval and a terminal analysis outcome.'}</dd></>}
          </dl>
          {features.githubChecks && row.check?.state === 'PUBLISHED' && <p className="subtle">Published means the result reached GitHub. Open the check or saved analysis to see whether it passed.</p>}
          <div className="actions">
            {row.analysisId && <button className="quiet" disabled={busy} onClick={() => void run(() => api<SavedAnalysis>(`/analyses/${row.analysisId}`, token), loaded)}>Open saved analysis</button>}
            {features.githubChecks && row.check?.state === 'FAILED' && <button disabled={busy} onClick={() => retry(row)}>Retry check publication</button>}
            {features.githubChecks && checkUrl && <a className="webhook-link" href={checkUrl} target="_blank" rel="noopener noreferrer">Open GitHub check ↗</a>}
          </div>
        </article>;
      })}</div>
      <div className="actions"><button className="quiet" disabled={busy || offset === 0} onClick={() => loadDeliveries(subscription.id, Math.max(0, offset - pageSize))}>Previous deliveries</button>
        <button className="quiet" disabled={busy || deliveries.length < pageSize || offset >= 100000} onClick={() => loadDeliveries(subscription.id, offset + pageSize)}>Next deliveries</button>
        <span className="subtle">Page {Math.floor(offset / pageSize) + 1}</span></div>
    </>}
  </details>;
}
