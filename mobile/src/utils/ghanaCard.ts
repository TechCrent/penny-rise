const DIGIT_COUNT = 10;

/**
 * Formats raw text input into the GHA-XXXXXXXXX-X mask as the user types.
 * Strips everything but digits, caps at 10 digits, and re-inserts the GHA-
 * prefix and dashes — so the prefix can't be deleted (any edit that removes
 * it just gets reformatted back) and the field can never grow past the
 * canonical 15-character shape.
 */
export function formatGhanaCardInput(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, DIGIT_COUNT);
  if (digits.length === 0) return 'GHA-';
  if (digits.length <= 9) return `GHA-${digits}`;
  return `GHA-${digits.slice(0, 9)}-${digits.slice(9)}`;
}
