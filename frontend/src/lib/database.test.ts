import { expect, test } from 'vitest';
import { databaseSample, parseDatabaseInput } from './database';
const parse = (before = databaseSample.before, after = databaseSample.after, dependencies = databaseSample.dependencies) => parseDatabaseInput('payments', before, after, dependencies);
test('sample creates a bounded schema request without credentials or tenant fields', () => {
  const result = parse(); expect(Object.keys(result).sort()).toEqual(['after', 'before', 'dependencies', 'service']);
  expect(result.before.dialect).toBe('postgresql'); expect(result.after.dialect).toBe('postgresql');
});
test('SQL, wrong dialects and array tables do not become schema requests', () => {
  expect(() => parse('ALTER TABLE payments DROP COLUMN amount')).toThrow('Before schema');
  expect(() => parse('{"dialect":"mysql","tables":{}}')).toThrow('postgresql');
  expect(() => parse('{"dialect":"postgresql","tables":[]}')).toThrow('tables object');
});
test('service and graph identifiers remain bounded and explicit', () => {
  expect(() => parseDatabaseInput('x'.repeat(101), databaseSample.before, databaseSample.after, '{}')).toThrow('identifier');
  expect(() => parse(undefined, undefined, '{"orders ":["payments"]}')).toThrow('without whitespace');
  expect(() => parse(undefined, undefined, '{"orders":"payments"}')).toThrow('provider name lists');
});
test('oversized schema documents are rejected before a request is sent', () => {
  expect(() => parse(JSON.stringify({ dialect: 'postgresql', tables: {}, padding: 'a'.repeat(1048576) }))).toThrow('1 MiB');
});
