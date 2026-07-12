import { formatGhanaCardInput } from '../src/utils/ghanaCard';

describe('formatGhanaCardInput', () => {
  it('empty input becomes just the GHA- prefix', () => {
    expect(formatGhanaCardInput('')).toBe('GHA-');
  });

  it('digits typed progressively build up before the second dash', () => {
    expect(formatGhanaCardInput('GHA-1')).toBe('GHA-1');
    expect(formatGhanaCardInput('GHA-123456789')).toBe('GHA-123456789');
  });

  it('the 10th digit inserts the second dash', () => {
    expect(formatGhanaCardInput('GHA-1234567890')).toBe('GHA-123456789-0');
  });

  it('strips non-digit characters (letters typed into the number portion)', () => {
    expect(formatGhanaCardInput('GHA-12a3b4')).toBe('GHA-1234');
  });

  it('caps at 10 digits total — further input is ignored', () => {
    expect(formatGhanaCardInput('GHA-12345678901234')).toBe('GHA-123456789-0');
  });

  it('deleting back into the prefix snaps back to GHA- (prefix cannot be removed)', () => {
    expect(formatGhanaCardInput('GH')).toBe('GHA-');
    expect(formatGhanaCardInput('')).toBe('GHA-');
  });

  it('a full valid card number round-trips unchanged', () => {
    expect(formatGhanaCardInput('GHA-000000001-1')).toBe('GHA-000000001-1');
  });
});
