import { useEffect, useRef, useState } from 'react';
import { api } from '../lib/api';
import { databaseSample, parseDatabaseInput } from '../lib/database';
import type { DatabaseSchemaReport, SavedDatabaseAnalysis, Summary } from '../types';

type Props = { token: string; busy: boolean; fail: (message: string) => void; execute: <T>(task: () => Promise<T>, done: (value: T) => void) => Promise<void> };
export function DatabasePanel({ token, busy, fail, execute }: Props) {
  const [fields, setFields] = useState(databaseSample);
  const [report, setReport] = useState<DatabaseSchemaReport | null>(null);
  const [saved, setSaved] = useState<SavedDatabaseAnalysis | null>(null);
  const [message, setMessage] = useState('');
  const [historyService, setHistoryService] = useState('payments');
  const [history, setHistory] = useState<Summary[]>([]);
  const [page, setPage] = useState({ service: '', offset: 0 });
  const [evidence, setEvidence] = useState(false);
  const epoch = useRef(0);
  useEffect(() => () => { epoch.current++; }, []);
  function run<T>(task: () => Promise<T>, done: (value: T) => void) {
    const current = epoch.current;
    return execute(async () => { try { return { value: await task() }; } catch (error) { if (current !== epoch.current) return null; throw error; } },
      result => { if (result && current === epoch.current) done(result.value); });
  }
  function analyze(save: boolean) {
    try {
      const input = parseDatabaseInput(fields.service, fields.before, fields.after, fields.dependencies);
      setMessage(''); setEvidence(false);
      if (save) void run(() => api<SavedDatabaseAnalysis>('/database-analyses', token, input), result => {
        setSaved(result); setReport(result.report); setHistory([]); setPage({ service: '', offset: 0 }); setMessage('Database schema analysis and supplied evidence saved.');
      });
      else void run(() => api<DatabaseSchemaReport>('/impact/database', token, input), result => {
        setSaved(null); setReport(result); setMessage('Database preview complete. This report has not been saved.');
      });
    } catch (error) { fail(error instanceof Error ? error.message : 'Invalid schema input.'); }
  }
  function loadHistory(service: string, offset: number) {
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/.test(service.trim())) { fail('Enter a valid database service identifier.'); return; }
    const id = service.trim();
    void run(() => api<Summary[]>(`/database-analyses?service=${encodeURIComponent(id)}&limit=20&offset=${offset}`, token), result => { setHistory(result); setPage({ service: id, offset }); });
  }
  function open(id: string) {
    void run(() => api<SavedDatabaseAnalysis>(`/database-analyses/${id}`, token), result => {
      setSaved(result); setReport(result.report); setEvidence(false);
      setFields({ service: result.service, before: JSON.stringify(result.before, null, 2), after: JSON.stringify(result.after, null, 2), dependencies: JSON.stringify(result.dependencies, null, 2) });
      setMessage('Saved database analysis loaded. Editors show its submitted evidence.');
    });
  }
  return <section className="card database-panel"><details><summary>Database schema changes</summary>
    <div className="section-heading"><div><span className="eyebrow">DATABASE COMPATIBILITY</span><h2>Compare PostgreSQL schemas</h2></div>
      <button className="quiet" disabled={busy} onClick={() => { setFields(databaseSample); setReport(null); setSaved(null); setEvidence(false); setMessage(''); }}>Load database example</button></div>
    <p>Compare before and after schema snapshots to find changes that may affect stored data, existing queries and application mappings.</p>
    <p className="subtle">Use normalized PostgreSQL JSON, not SQL migration files. No SQL is executed and no target database connection is opened.</p>
    <label htmlFor="db-service">Database service</label><input id="db-service" maxLength={100} value={fields.service} disabled={busy} onChange={event => setFields(current => ({ ...current, service: event.target.value }))}/>
    <div className="editors">{(['before', 'after'] as const).map(key => <div key={key}><label htmlFor={'db-' + key}>{key === 'before' ? 'Before schema' : 'After schema'} <span>PostgreSQL JSON</span></label>
      <textarea id={'db-' + key} spellCheck={false} disabled={busy} value={fields[key]} onChange={event => setFields(current => ({ ...current, [key]: event.target.value }))}/></div>)}</div>
    <label htmlFor="db-dependencies">Database consumer dependencies</label><textarea id="db-dependencies" className="graph-input" disabled={busy} spellCheck={false} value={fields.dependencies} onChange={event => setFields(current => ({ ...current, dependencies: event.target.value }))}/>
    <details><summary>Supported schema format</summary><p>Set dialect to postgresql. Tables use schema-qualified names such as public.payments. Define columns with a supported type and explicit nullable flag. VARCHAR requires length; bounded NUMERIC requires precision and scale. Optional primaryKey, unique and foreignKeys reference modeled columns.</p>
      <p>Unsupported fields and types return an error. Default presence can be declared with hasDefault; expressions are not modeled. Renames appear as removals and additions.</p></details>
    <div className="actions"><button disabled={busy} onClick={() => analyze(true)}>Analyze database & save</button><button className="secondary" disabled={busy} onClick={() => analyze(false)}>Preview database changes</button></div>
    {message && <div className="notice" role="status">{message}</div>}
    {report && <div className="database-report"><div className="section-heading"><h3>Database findings · {report.service}</h3><span className={'badge ' + report.risk.toLowerCase()}>{report.risk} risk</span></div>
      <p className="subtle">Result for the last submitted comparison. Editors may have changed since it was analyzed.</p>
      <div className="stats"><div><strong>{report.changes.length}</strong><span>schema changes</span></div><div><strong>{report.potentialConsumers.length}</strong><span>potential consumers</span></div><div><strong>{report.requiresReview ? 'Required' : 'Advisory'}</strong><span>human review</span></div></div>
      {report.changes.length ? <div className="table-wrap"><table><thead><tr><th>Table / location</th><th>Change</th><th>Risk / evidence</th></tr></thead><tbody>{report.changes.map((change, index) => <tr key={index}><td data-label="Table / location"><code>{change.table}</code><small><code>{change.location}</code></small></td><td data-label="Change">{change.code.replaceAll('_', ' ').toLowerCase()}</td><td data-label="Risk / evidence"><strong>{change.severity}</strong><small>{change.explanation}</small></td></tr>)}</tbody></table></div> : <p>No changes detected within the supported schema subset.</p>}
      <h3>Potential database consumers</h3><ul className="paths">{report.potentialConsumers.map(consumer => <li key={consumer.service}><strong>{consumer.service}</strong><code>{consumer.path.join(' → ')}</code></li>)}</ul>
      {!report.potentialConsumers.length && <p>No potential consumers identified from the supplied graph.</p>}
      <h3>Suggested validation</h3><ul>{report.recommendedTests.map(test => <li key={test}>{test}</li>)}</ul>
      <details><summary>Database analysis limitations</summary><ul>{report.limitations.map(limit => <li key={limit}>{limit}</li>)}</ul></details>
      {saved && <div className="saved"><small>Saved {new Date(saved.createdAt).toLocaleString()} · {saved.id}</small><button className="quiet" onClick={() => setEvidence(!evidence)}>{evidence ? 'Hide saved schema evidence' : 'View saved schema evidence'}</button>
        {evidence && <><h4>Saved before schema</h4><pre>{JSON.stringify(saved.before, null, 2)}</pre><h4>Saved after schema</h4><pre>{JSON.stringify(saved.after, null, 2)}</pre><h4>Saved dependency graph</h4><pre>{JSON.stringify(saved.dependencies, null, 2)}</pre></>}</div>}
    </div>}
    <h3>Database analysis history</h3><form className="history-filter" onSubmit={event => { event.preventDefault(); loadHistory(historyService, 0); }}>
      <label htmlFor="db-history-service">Database history service</label><input id="db-history-service" required maxLength={100} value={historyService} disabled={busy} onChange={event => setHistoryService(event.target.value)}/><button className="secondary" disabled={busy}>Load database history</button></form>
    <div>{history.map(item => <button className="history-row database-history-row" key={item.id} disabled={busy} onClick={() => open(item.id)}><span><strong>{item.service}</strong><small>{new Date(item.createdAt).toLocaleString()}</small></span><span className={'badge ' + item.risk.toLowerCase()}>{item.risk}</span><span aria-hidden="true">↗</span></button>)}</div>
    {!history.length && <p className="history-empty">{page.service ? 'No database analyses on this page.' : 'Load saved database analyses for a service.'}</p>}
    {page.service && <div className="actions"><button className="quiet" disabled={busy || page.offset === 0} onClick={() => loadHistory(page.service, page.offset - 20)}>Previous database analyses</button><span className="subtle">Page {Math.floor(page.offset / 20) + 1}</span><button className="quiet" disabled={busy || history.length < 20 || page.offset >= 100000} onClick={() => loadHistory(page.service, page.offset + 20)}>Next database analyses</button></div>}
  </details></section>;
}
