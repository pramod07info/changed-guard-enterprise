import { useState } from 'react';
import type { Summary } from '../types';

export function HistoryPanel({ entries, load, select, busy, offset, lastService }: {
  entries: Summary[]; load: (service: string, offset: number) => void; select: (id: string) => void;
  busy: boolean; offset: number; lastService: string
}) {
  const [service, setService] = useState('payments');
  return <section className="card">
    <div className="section-heading"><div><span className="eyebrow">03 · TRACE</span><h2>Analysis history</h2></div><span className="subtle">Current tenant only</span></div>
    <form className="history-filter" onSubmit={e => { e.preventDefault(); load(service, 0); }}>
      <label htmlFor="history-service">Service</label><input id="history-service" value={service} onChange={e => setService(e.target.value)} required/>
      <button className="secondary" disabled={busy}>Load history</button></form>
    {entries.length ? <div className="history-list">{entries.map(entry => <button className="history-row" key={entry.id} disabled={busy} onClick={() => select(entry.id)}>
      <span><strong>{entry.service}</strong><small>{new Date(entry.createdAt).toLocaleString()}</small></span><span className={'badge ' + entry.risk.toLowerCase()}>{entry.risk}</span><span aria-hidden="true">↗</span></button>)}</div> :
      <p className="history-empty">{lastService ? 'No saved analyses on this page.' : 'Choose a service to load its saved analyses.'}</p>}
    {lastService && <div className="actions"><button className="quiet" disabled={busy || offset === 0} onClick={() => load(lastService, offset - 20)}>Previous</button>
      <span className="subtle">Page {offset / 20 + 1}</span><button className="quiet" disabled={busy || entries.length < 20} onClick={() => load(lastService, offset + 20)}>Next</button></div>}
  </section>;
}
