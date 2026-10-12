import type { Features } from '../lib/features';
import { useState } from 'react';
import { sample } from '../sample';
import { parseInput } from '../lib/input';
import type { AnalysisInput } from '../types';

export function AnalysisForm({ features, submit, busy, fail }: { features: Features; submit: (input: AnalysisInput, save: boolean) => void; busy: boolean; fail: (message: string) => void }) {
  const [fields, setFields] = useState(sample);
  const update = (key: keyof typeof sample, value: string) => setFields(previous => ({ ...previous, [key]: value }));
  function send(save: boolean) {
    try { submit(parseInput(fields.service, fields.before, fields.after, features.dependencyAnalysis ? fields.dependencies : '{}'), save); }
    catch (error) { fail(error instanceof Error ? error.message : 'Invalid input.'); }
  }
  return <section className="card">
    <div className="section-heading"><div><span className="eyebrow">01 · COMPARE</span><h2>Analyze a contract change</h2></div>
      <button className="quiet" disabled={busy} onClick={() => setFields(sample)}>Load example</button></div>
    <label htmlFor="service">Service name</label><input id="service" maxLength={200} value={fields.service} onChange={e => update('service', e.target.value)}/>
    <div className="editors">{(['before', 'after'] as const).map(key => <div key={key}>
      <label htmlFor={key}>{key === 'before' ? 'Before contract' : 'After contract'} <span>OpenAPI 3.0 JSON</span></label>
      <textarea id={key} spellCheck={false} value={fields[key]} onChange={e => update(key, e.target.value)}/>
    </div>)}</div>
    {features.dependencyAnalysis && <><label htmlFor="dependencies">Service dependencies <span>consumer → providers</span></label>
    <textarea id="dependencies" className="graph-input" spellCheck={false} value={fields.dependencies} onChange={e => update('dependencies', e.target.value)}/></>}
    <div className="actions">{features.analysisHistory && <button disabled={busy} onClick={() => send(true)}>Analyze & save</button>}
      <button className="secondary" disabled={busy} onClick={() => send(false)}>Preview analysis</button></div>
    <small>Unsupported contract features return an error. Reports identify potential impact and require human review.</small>
  </section>;
}
