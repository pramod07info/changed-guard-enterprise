import { useState } from 'react';
import { api } from '../lib/api';
import { parseInput } from '../lib/input';
import { sample } from '../sample';
import type { Baseline, BaselineSummary, CatalogService, Project, Report, SavedAnalysis } from '../types';

type Props = {
  token: string; busy: boolean; fail: (message: string) => void;
  execute: <T>(task: () => Promise<T>, done: (value: T) => void) => Promise<void>;
  compared: (report: Report, saved: SavedAnalysis | null) => void;
};
const pageSize = 20;
export function CatalogPanel({ token, busy, fail, execute, compared }: Props) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [project, setProject] = useState('');
  const [projectOffset, setProjectOffset] = useState(0);
  const [projectName, setProjectName] = useState('');
  const [services, setServices] = useState<CatalogService[]>([]);
  const [service, setService] = useState<CatalogService | null>(null);
  const [serviceOffset, setServiceOffset] = useState(0);
  const [name, setName] = useState('');
  const [owner, setOwner] = useState('');
  const [repositoryUrl, setRepositoryUrl] = useState('');
  const [baselines, setBaselines] = useState<BaselineSummary[]>([]);
  const [baseline, setBaseline] = useState<Baseline | null>(null);
  const [baselineOffset, setBaselineOffset] = useState(0);
  const [version, setVersion] = useState('');
  const [environment, setEnvironment] = useState('development');
  const [document, setDocument] = useState(sample.before);
  const [after, setAfter] = useState(sample.after);
  const [dependencies, setDependencies] = useState(sample.dependencies);

  function clearService() { setService(null); setBaselines([]); setBaseline(null); setBaselineOffset(0); }
  function selectProject(id: string) {
    setProject(id); setServices([]); setServiceOffset(0); clearService();
    if (id) loadServices(id, 0);
  }
  function loadProjects(offset: number) {
    void execute(() => api<Project[]>(`/projects?limit=${pageSize}&offset=${offset}`, token), result => {
      setProjects(result); setProjectOffset(offset); setProject(''); setServices([]); clearService();
    });
  }
  function loadServices(id: string, offset: number) {
    void execute(() => api<CatalogService[]>(`/projects/${id}/services?limit=${pageSize}&offset=${offset}`, token), result => {
      setServices(result); setServiceOffset(offset); clearService();
    });
  }
  function loadBaselines(id: string, offset: number) {
    void execute(() => api<BaselineSummary[]>(`/services/${id}/baselines?limit=${pageSize}&offset=${offset}`, token), result => {
      setBaselines(result); setBaselineOffset(offset); setBaseline(null);
    });
  }
  function selectService(id: string) {
    const selected = services.find(item => item.id === id) ?? null;
    setService(selected); setBaselines([]); setBaseline(null); setBaselineOffset(0);
    if (selected) loadBaselines(selected.id, 0);
  }
  function publish() {
    if (!service) return;
    try {
      const parsed = parseInput(service.name, document, document, '{}');
      void execute(() => api<Baseline>(`/services/${service.id}/baselines`, token,
        { version, environment, document: parsed.before }), result => {
          setBaseline(result); setBaselines([result]); setBaselineOffset(0); setVersion('');
        });
    } catch (error) { fail(error instanceof Error ? error.message : 'Invalid baseline.'); }
  }
  function compare(save: boolean) {
    if (!baseline) return;
    try {
      const input = parseInput(baseline.serviceName, JSON.stringify(baseline.document), after, dependencies);
      const body = { after: input.after, dependencies: input.dependencies };
      if (save) void execute(() => api<SavedAnalysis>(`/baselines/${baseline.id}/analyses`, token, body), result => compared(result.report, result));
      else void execute(() => api<Report>(`/baselines/${baseline.id}/preview`, token, body), result => compared(result, null));
    } catch (error) { fail(error instanceof Error ? error.message : 'Invalid comparison.'); }
  }
  return <section className="card catalog"><details><summary>Project catalog & contract baselines</summary>
    <p>Register a service, publish a versioned baseline, then compare a proposed contract. Publishing is explicit and cannot overwrite an existing version.</p>
    <h3>Projects</h3>
    <form onSubmit={event => { event.preventDefault(); void execute(() => api<Project>('/projects', token, { name: projectName }), result => {
      setProjects([result]); setProjectName(''); setProjectOffset(0); selectProject(result.id);
    }); }}>
      <label htmlFor="project-name">New project name</label><input id="project-name" maxLength={200} required value={projectName} onChange={event => setProjectName(event.target.value)}/>
      <button disabled={busy}>Create project</button>
    </form>
    <div className="actions"><button disabled={busy} className="secondary" onClick={() => loadProjects(0)}>Load projects</button>
      <button disabled={busy || !projectOffset} className="quiet" onClick={() => loadProjects(Math.max(0, projectOffset - pageSize))}>Previous projects</button>
      <button disabled={busy || projects.length < pageSize} className="quiet" onClick={() => loadProjects(projectOffset + pageSize)}>Next projects</button></div>
    <label htmlFor="catalog-project">Project</label><select id="catalog-project" disabled={busy} value={project} onChange={event => selectProject(event.target.value)}>
      <option value="">Select a project</option>{projects.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
    </select>
    {project && <>
      <h3>Services</h3><form onSubmit={event => { event.preventDefault(); void execute(() => api<CatalogService>(`/projects/${project}/services`, token, { name, owner, repositoryUrl }), result => {
        setServices([result]); setService(result); setServiceOffset(0); setName(''); setOwner(''); setRepositoryUrl(''); setBaselines([]); setBaseline(null);
      }); }}>
        <label htmlFor="catalog-service-name">New service name</label><input id="catalog-service-name" maxLength={100} required value={name} onChange={event => setName(event.target.value)}/>
        <label htmlFor="catalog-owner">Service owner</label><input id="catalog-owner" maxLength={200} required value={owner} onChange={event => setOwner(event.target.value)}/>
        <label htmlFor="catalog-repository">Repository URL (optional)</label><input id="catalog-repository" type="url" maxLength={500} value={repositoryUrl} onChange={event => setRepositoryUrl(event.target.value)} placeholder="https://github.com/team/repository"/>
        <button disabled={busy}>Register service</button>
      </form>
      <div className="actions"><button disabled={busy} className="secondary" onClick={() => loadServices(project, 0)}>Load services</button>
        <button disabled={busy || !serviceOffset} className="quiet" onClick={() => loadServices(project, Math.max(0, serviceOffset - pageSize))}>Previous services</button>
        <button disabled={busy || services.length < pageSize} className="quiet" onClick={() => loadServices(project, serviceOffset + pageSize)}>Next services</button></div>
      <label htmlFor="catalog-service">Registered service</label><select id="catalog-service" disabled={busy} value={service?.id ?? ''} onChange={event => selectService(event.target.value)}>
        <option value="">Select a service</option>{services.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
      </select>
    </>}
    {service && <>
      <p>Owner: {service.owner}{service.repositoryUrl && <> · Repository: {service.repositoryUrl}</>}</p>
      <h3>Publish a baseline</h3><form onSubmit={event => { event.preventDefault(); publish(); }}>
        <label htmlFor="baseline-version">Baseline version</label><input id="baseline-version" maxLength={100} required value={version} onChange={event => setVersion(event.target.value)}/>
        <label htmlFor="baseline-environment">Baseline environment</label><input id="baseline-environment" maxLength={100} required value={environment} onChange={event => setEnvironment(event.target.value)}/>
        <label htmlFor="baseline-document">Baseline contract (OpenAPI 3.0 JSON)</label><textarea id="baseline-document" spellCheck={false} value={document} onChange={event => setDocument(event.target.value)}/>
        <button disabled={busy}>Publish baseline</button>
      </form>
      <small>All tenant members can publish in this phase. Owner is metadata, not an authorization role. Dedicated approval permissions are planned.</small>
      <div className="actions"><button disabled={busy} className="secondary" onClick={() => loadBaselines(service.id, 0)}>Load baselines</button>
        <button disabled={busy || !baselineOffset} className="quiet" onClick={() => loadBaselines(service.id, Math.max(0, baselineOffset - pageSize))}>Previous baselines</button>
        <button disabled={busy || baselines.length < pageSize} className="quiet" onClick={() => loadBaselines(service.id, baselineOffset + pageSize)}>Next baselines</button></div>
      <label htmlFor="catalog-baseline">Published baseline</label><select id="catalog-baseline" disabled={busy} value={baseline?.id ?? ''} onChange={event => {
        setBaseline(null); if (event.target.value) void execute(() => api<Baseline>(`/baselines/${event.target.value}`, token), setBaseline);
      }}><option value="">Select a baseline</option>{baselines.map(item => <option key={item.id} value={item.id}>{item.environment} · {item.version}</option>)}</select>
    </>}
    {baseline && <>
      <p>Baseline {baseline.version} · {baseline.environment} · Published by {baseline.publishedBy}</p>
      <label htmlFor="published-contract">Published contract (read only)</label><textarea id="published-contract" readOnly value={JSON.stringify(baseline.document, null, 2)}/>
      <label htmlFor="baseline-candidate">Proposed contract</label><textarea id="baseline-candidate" spellCheck={false} value={after} onChange={event => setAfter(event.target.value)}/>
      <label htmlFor="baseline-dependencies">Baseline comparison dependencies</label><textarea id="baseline-dependencies" spellCheck={false} value={dependencies} onChange={event => setDependencies(event.target.value)}/>
      <div className="actions"><button disabled={busy} onClick={() => compare(true)}>Analyze baseline & save</button>
        <button disabled={busy} className="secondary" onClick={() => compare(false)}>Preview against baseline</button></div>
    </>}
  </details></section>;
}
