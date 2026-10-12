import { api } from './api';

export const featureNames = ['normalizedAnalysis', 'openApiAnalysis', 'analysisHistory', 'catalog', 'baselines', 'databaseAnalysis', 'databaseHistory', 'dependencyAnalysis', 'testRecommendations', 'githubPr', 'webhooks', 'githubChecks', 'metrics'] as const;
export type Features = Record<typeof featureNames[number], boolean>;
/** Missing or malformed capabilities must never enable a control. */
export function parseFeatures(value: unknown): Features {
  if (!value || typeof value !== 'object' || Array.isArray(value) ||
      featureNames.some(key => typeof (value as Record<string, unknown>)[key] !== 'boolean')) {
    throw new Error('Unable to load server features. Sign in again to retry.');
  }
  return Object.fromEntries(featureNames.map(key => [key, (value as Features)[key]])) as Features;
}
export async function loadFeatures(token: string): Promise<Features> {
  return parseFeatures(await api<unknown>('/features', token));
}
