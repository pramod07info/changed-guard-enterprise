import { describe, expect, it } from 'vitest';
import { featureNames, parseFeatures } from './features';

describe('server capabilities', () => {
  it('preserves all true and all false manifests', () => {
    for (const enabled of [true, false]) {
      const input = Object.fromEntries(featureNames.map(key => [key, enabled]));
      expect(parseFeatures(input)).toEqual(input);
    }
  });
  it('rejects missing, nonboolean and unavailable manifests', () => {
    const valid = Object.fromEntries(featureNames.map(key => [key, true]));
    for (const value of [null, [], {}, { ...valid, catalog: 'true' }, { ...valid, webhooks: undefined }]) {
      expect(() => parseFeatures(value)).toThrow('Unable to load server features');
    }
  });
  it('ignores unknown keys without exposing operator metadata', () => {
    const valid = Object.fromEntries(featureNames.map(key => [key, false]));
    expect(parseFeatures({ ...valid, secret: 'do-not-display' })).toEqual(valid);
  });
});
