import { useEffect, useRef, useState } from 'react';
import { signIn, finishSignIn, signOut } from './lib/auth';
import { loadFeatures, type Features } from './lib/features';
import { api, ApiError } from './lib/api';
import { ConnectionPanel } from './components/ConnectionPanel';
import { AnalysisForm } from './components/AnalysisForm';
import { ReportPanel } from './components/ReportPanel';
import { HistoryPanel } from './components/HistoryPanel';
import { CatalogPanel } from './components/CatalogPanel';
import { DatabasePanel } from './components/DatabasePanel';
import type { AnalysisInput, Report, SavedAnalysis, Session, Snapshot, Summary } from './types';

export default function App() {
  const [connection, setConnection] = useState<{ token: string; session: Session; features: Features } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [report, setReport] = useState<Report | null>(null);
  const [saved, setSaved] = useState<SavedAnalysis | null>(null);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
  const [history, setHistory] = useState<Summary[]>([]);
  const [page, setPage] = useState({ service: '', offset: 0 });
  const generation = useRef(0);
  const pendingRequests = useRef(0);
  function disconnect() {
    generation.current++;
    pendingRequests.current = 0;
    setConnection(null); setReport(null); setSaved(null); setSnapshot(null); setHistory([]);
    setPage({ service: '', offset: 0 }); setNotice(''); setError(''); setBusy(false);
  }
  async function run<T>(task: () => Promise<T>, done: (value: T) => void) {
    const current = generation.current;
    pendingRequests.current++;
    setBusy(true); setError(''); setNotice('');
    try { const value = await task(); if (current === generation.current) done(value); }
    catch (failure) {
      if (current !== generation.current) return;
      if (failure instanceof ApiError && failure.status === 401) disconnect();
      setError(failure instanceof Error ? failure.message : 'Unable to complete the request.');
    } finally { if (current === generation.current) {
      pendingRequests.current--; setBusy(pendingRequests.current > 0);
    } }
  }
  const connect = (token: string) => {
    generation.current++;
    pendingRequests.current = 0;
    void run(() => Promise.all([api<Session>('/session', token), loadFeatures(token)]), ([session, features]) => setConnection({ token, session, features }));
  };
  const callbackStarted = useRef(false);
  useEffect(() => {
    if (callbackStarted.current) return;
    callbackStarted.current = true;
    void run(finishSignIn, token => { if (token) connect(token); });
  }, []);
  const login = () => void run(signIn, () => {});
  const logout = () => { disconnect(); void run(signOut, () => {}); };
  function analyze(input: AnalysisInput, persist: boolean) {
    if (!connection) return;
    setSnapshot(null);
    if (persist) void run(() => api<SavedAnalysis>('/analyses', connection.token, input), result => {
      setSaved(result); setReport(result.report); setNotice('Analysis and contract snapshots saved.');
      setHistory([]); setPage({ service: '', offset: 0 });
    });
    else void run(() => api<Report>('/impact/openapi', connection.token, input), result => {
      setSaved(null); setReport(result); setNotice('Preview complete. This report has not been saved.');
    });
  }
  function loadHistory(service: string, offset: number) {
    if (!connection) return;
    void run(() => api<Summary[]>('/analyses?service=' + encodeURIComponent(service) + '&limit=20&offset=' + offset, connection.token), result => {
      setHistory(result); setPage({ service, offset });
    });
  }
  return <><header className="topbar"><a className="brand" href="/"><span className="brand-mark">◇</span>ChangeGuard<span className="brand-label">ENTERPRISE</span></a>
    {connection && <div className="identity"><span><small>WORKSPACE</small>{connection.session.tenantId}</span><button className="quiet" onClick={logout}>Sign out</button></div>}</header>
    <main><div className="page-intro"><span className="eyebrow">CHANGE INTELLIGENCE</span><h1>Know the impact. Own the change.</h1><p>Evidence for the teams and services behind every API.</p></div>
      {error && <div className="alert" role="alert">{error}</div>}{notice && <div className="notice" role="status">{notice}</div>}
      {busy && <p role="status" className="subtle">Working…</p>}
      {!connection ? <ConnectionPanel login={login} busy={busy}/> : <>
        {connection.features.catalog && <CatalogPanel features={connection.features} token={connection.token} busy={busy} fail={setError} execute={run} loaded={result => {
          setSaved(result); setReport(result.report); setSnapshot(null); setNotice('Saved webhook analysis loaded.');
        }} compared={(result, persisted) => {
          setReport(result); setSaved(persisted); setSnapshot(null); setHistory([]); setPage({ service: '', offset: 0 });
          setNotice(persisted ? 'Baseline comparison and evidence saved.' : 'Baseline preview complete. This report has not been saved.');
        }}/>}
        {connection.features.databaseAnalysis && <DatabasePanel features={connection.features} token={connection.token} busy={busy} fail={setError} execute={run}/>}
        {connection.features.openApiAnalysis && <AnalysisForm features={connection.features} submit={analyze} busy={busy} fail={setError}/>}
        {(connection.features.openApiAnalysis || connection.features.analysisHistory) && <ReportPanel features={connection.features} report={report} saved={saved} busy={busy} snapshot={id => void run(() => api<Snapshot>('/snapshots/' + id, connection.token), setSnapshot)}/>}
        {snapshot && <section className="card"><div className="section-heading"><h2>Contract snapshot</h2><button className="quiet" onClick={() => setSnapshot(null)}>Close</button></div><pre>{JSON.stringify(snapshot.document, null, 2)}</pre></section>}
        {connection.features.analysisHistory && <HistoryPanel entries={history} busy={busy} offset={page.offset} lastService={page.service} load={loadHistory}
          select={id => void run(() => api<SavedAnalysis>('/analyses/' + id, connection.token), result => { setSaved(result); setReport(result.report); setSnapshot(null); setNotice('Saved analysis loaded.'); })}/>}
        {!connection.features.catalog && !connection.features.openApiAnalysis && !connection.features.databaseAnalysis && <section className="card"><h2>No dashboard features enabled</h2><p>Your server operator has disabled the available workspace modules.</p></section>}
      </>}
    </main><footer>ChangeGuard · Compatibility evidence, reviewed by people.</footer></>;
}
