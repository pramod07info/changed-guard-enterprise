import { useState } from 'react';
import { api } from '../lib/api';
import { parseGitHubPr } from '../lib/github';
import type { Baseline, GitHubSource, GitHubPrPreview, Report, SavedAnalysis } from '../types';

type Props = {
  baseline: Baseline; repositoryUrl: string | null; dependencies: string; token: string; busy: boolean;
  fail: (message: string) => void;
  execute: <T>(task: () => Promise<T>, done: (value: T) => void) => Promise<void>;
  compared: (report: Report, saved: SavedAnalysis | null) => void;
};
export function GitHubPrPanel({ baseline, repositoryUrl, dependencies, token, busy, fail, execute, compared }: Props) {
  const [pull, setPull] = useState('');
  const [path, setPath] = useState('openapi.json');
  const [source, setSource] = useState<GitHubSource | null>(null);
  function compare(save: boolean) {
    try {
      const input = parseGitHubPr(pull, path, dependencies);
      const route = `/baselines/${baseline.id}/github-pr/`;
      if (save) void execute(() => api<SavedAnalysis>(route + 'analyses', token, input), result => {
        setSource(result.githubSource ?? null); compared(result.report, result);
      });
      else void execute(() => api<GitHubPrPreview>(route + 'preview', token, input), result => {
        setSource(result.source); compared(result.report, null);
      });
    } catch (error) { fail(error instanceof Error ? error.message : 'Invalid PR input.'); }
  }
  return <><h3>Compare a GitHub pull request</h3>
    <p>Read a public PR contract at its pinned commit and compare it against baseline {baseline.version}. No GitHub comments, checks or repository changes are made.</p>
    <small>Uses the registered service repository and the dependency map above. Enable CHANGEGUARD_GITHUB_ENABLED on the backend.</small>
    <label htmlFor="github-pr-number">GitHub PR number</label><input id="github-pr-number" type="number" min="1" max="2147483647" value={pull} onChange={event => setPull(event.target.value)}/>
    <label htmlFor="github-contract-path">PR contract file path</label><input id="github-contract-path" maxLength={500} value={path} onChange={event => setPath(event.target.value)}/>
    {!repositoryUrl && <p>Register a GitHub repository URL for this service first.</p>}
    <div className="actions"><button disabled={busy || !repositoryUrl || !pull} onClick={() => compare(true)}>Analyze GitHub PR & save</button>
      <button disabled={busy || !repositoryUrl || !pull} className="secondary" onClick={() => compare(false)}>Preview GitHub PR</button></div>
    {source && <p>Analyzed {source.repository} PR #{source.pullNumber} · Head <code>{source.headSha}</code> · File <code>{source.contractPath}</code></p>}
  </>;
}
