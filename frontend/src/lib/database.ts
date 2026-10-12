import { parseJsonObject, parseDependencies } from './input';
import type { DatabaseSchemaInput } from '../types';
const before = { dialect: 'postgresql', tables: { 'public.payments': { columns: { id: { type: 'uuid', nullable: false }, amount: { type: 'numeric', nullable: false, precision: 10, scale: 2 }, reference: { type: 'varchar', nullable: true, length: 100 } }, primaryKey: ['id'] } } };
const after = { dialect: 'postgresql', tables: { 'public.payments': { columns: { id: { type: 'uuid', nullable: false }, amount: { type: 'numeric', nullable: false, precision: 8, scale: 2 } }, primaryKey: ['id'] } } };
export const databaseSample = { service: 'payments', before: JSON.stringify(before, null, 2), after: JSON.stringify(after, null, 2), dependencies: JSON.stringify({ orders: ['payments'], checkout: ['orders'] }, null, 2) };
export function parseDatabaseInput(service: string, before: string, after: string, dependencies: string): DatabaseSchemaInput {
  const id = service.trim();
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/.test(id)) throw new Error('Database service must use 1–100 identifier characters.');
  const input = { service: id, before: parseJsonObject(before, 'Before schema'), after: parseJsonObject(after, 'After schema'), dependencies: parseDependencies(dependencies) };
  for (const document of [input.before, input.after]) {
    if (document.dialect !== 'postgresql' || document.tables === null || typeof document.tables !== 'object' || Array.isArray(document.tables))
      throw new Error('Each schema must use dialect postgresql and a tables object.');
    if (new TextEncoder().encode(JSON.stringify(document)).length > 1048576) throw new Error('Each schema must not exceed 1 MiB.');
  }
  const entries = Object.entries(input.dependencies);
  if (entries.length > 500 || entries.reduce((n, [, values]) => n + values.length, 0) > 5000 || new TextEncoder().encode(JSON.stringify(input.dependencies)).length > 65536)
    throw new Error('Database dependencies exceed supported bounds.');
  if (entries.some(([key, values]) => [key, ...values].some(value => !/^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/.test(value))))
    throw new Error('Dependency names must use 1–100 identifier characters without whitespace.');
  return input;
}
