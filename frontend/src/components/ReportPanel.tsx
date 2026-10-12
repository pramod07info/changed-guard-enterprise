import type { Features } from '../lib/features';
import type { Report, SavedAnalysis } from '../types';

export function ReportPanel({ features, report, saved, snapshot, busy }: {
  features: Features; report: Report | null; saved: SavedAnalysis | null; snapshot: (id: string) => void; busy: boolean
}) {
  return <section className="card">
    <div className="section-heading"><div><span className="eyebrow">02 · UNDERSTAND</span><h2>Change impact</h2></div>
      {report && <span className={'badge ' + report.risk.toLowerCase()}>{report.risk === 'NONE' ? 'No change' : report.risk + ' risk'}</span>}</div>
    {!report ? <div className="empty"><span className="empty-icon">↗</span><h3>Every change has a story.</h3><p>Run an analysis to see compatibility findings, affected services, and suggested tests.</p></div> : <>
      <div className="stats"><div><strong>{report.breakingChanges.length}</strong><span>review findings</span></div>
        {features.dependencyAnalysis && <><div><strong>{report.potentialConsumers.length}</strong><span>potential consumers</span></div></>}
        <div><strong>{report.requiresReview ? 'Required' : 'Advisory'}</strong><span>human review</span></div></div>
      <h3>Compatibility findings</h3>
      {report.breakingChanges.length === 0 ? <p>No breaking changes detected within the supported subset.</p> :
        <div className="table-wrap"><table><thead><tr><th>Operation</th><th>Change</th><th>Evidence</th></tr></thead>
          <tbody>{report.breakingChanges.map((finding, i) => <tr key={i}><td><code>{finding.operation}</code></td><td>{finding.code.replaceAll('_', ' ').toLowerCase()}</td>
            <td>{finding.explanation}<small><code>{finding.location}</code></small></td></tr>)}</tbody></table></div>}
      {features.dependencyAnalysis && <><h3>Potential impact paths</h3><ul className="paths">{report.potentialConsumers.map(consumer => <li key={consumer.service}><strong>{consumer.service}</strong><code>{consumer.path.join(' → ')}</code></li>)}</ul>
      {!report.potentialConsumers.length && <p>No dependent services found in the supplied graph.</p>}</>}
      {features.testRecommendations && <><h3>Suggested tests</h3><ul>{report.recommendedTests.map(test => <li key={test}>{test}</li>)}</ul></>}
      <details><summary>Scope and limitations</summary><ul>{report.limitations.map(item => <li key={item}>{item}</li>)}</ul></details>
      {features.analysisHistory && saved && <div className="saved"><small>Saved {new Date(saved.createdAt).toLocaleString()} · {saved.id}</small>
        {saved.baselineId && <small>Compared against published baseline: {saved.baselineId}</small>}
        {saved.githubSource && <small>GitHub {saved.githubSource.repository} PR #{saved.githubSource.pullNumber} · Head <code>{saved.githubSource.headSha}</code> · File <code>{saved.githubSource.contractPath}</code> · Blob <code>{saved.githubSource.blobSha}</code></small>}<div className="actions">
        <button className="quiet" disabled={busy} onClick={() => snapshot(saved.beforeSnapshotId)}>Before snapshot</button>
        <button className="quiet" disabled={busy} onClick={() => snapshot(saved.afterSnapshotId)}>After snapshot</button></div></div>}
    </>}
  </section>;
}
