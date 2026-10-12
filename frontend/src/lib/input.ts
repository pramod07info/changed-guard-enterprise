import type { AnalysisInput, JsonObject } from '../types';

function object(text: string, name: string): JsonObject {
  let value: unknown;
  try { value = JSON.parse(text); } catch { throw new Error(name + ' must be valid JSON.'); }
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new Error(name + ' must be a JSON object.');
  return value as JsonObject;
}
export function parseDependencies(dependencies: string): Record<string, string[]> {
  const graph = object(dependencies, 'Dependencies');
  if (Object.entries(graph).some(([key, values]) => !key.trim() || !Array.isArray(values) || values.some(v => typeof v !== 'string' || !v.trim())))
    throw new Error('Dependencies must map consumer names to provider name lists.');
  return graph as Record<string, string[]>;
}
export function parseInput(service: string, before: string, after: string, dependencies: string): AnalysisInput {
  if (!service.trim() || service.length > 200) throw new Error('Service must contain 1–200 characters.');
  return { service, before: object(before, 'Before contract'), after: object(after, 'After contract'), dependencies: parseDependencies(dependencies) };
}
