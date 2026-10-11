import { describe, expect, it } from 'vitest';
import { parseInput } from './input';
import { sample } from '../sample';

describe('analysis input', () => {
  it('preserves the example contract and graph', () => {
    const result = parseInput(sample.service, sample.before, sample.after, sample.dependencies);
    expect(result.before.openapi).toBe('3.0.3');
    expect(result.dependencies.orders).toEqual(['payments']);
  });
  it('rejects invalid JSON, nonobjects and malformed graph values', () => {
    expect(() => parseInput('payments', '{', '{}', '{}')).toThrow('valid JSON');
    expect(() => parseInput('payments', 'null', '{}', '{}')).toThrow('JSON object');
    expect(() => parseInput('payments', '{}', '{}', '{"orders":"payments"}')).toThrow('provider name lists');
    expect(() => parseInput(' ', '{}', '{}', '{}')).toThrow('Service');
  });
});
