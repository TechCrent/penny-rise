import { describe, it, expect } from 'vitest';
import { maskGhanaCardNumber } from '../src/api/kycAdmin';

describe('maskGhanaCardNumber', () => {
  it('masks all but the last 4 digits of the middle segment', () => {
    expect(maskGhanaCardNumber('GHA-000000001-1')).toBe('GHA-*****0001-1');
  });

  it('handles short digit segments gracefully', () => {
    expect(maskGhanaCardNumber('GHA-123-1')).toBe('GHA-123-1');
  });

  it('returns — for empty input', () => {
    expect(maskGhanaCardNumber('')).toBe('—');
  });

  it('returns **** for malformed input', () => {
    expect(maskGhanaCardNumber('NOT-A-CARD')).toBe('****');
  });
});
