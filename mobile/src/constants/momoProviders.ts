// Shared by Deposit/Withdraw/EarlyExit screens, which each separately collect
// a MoMo provider + number. Mirrors the prefix validation payments-service and
// monolith enforce server-side (MomoNumberValidator in both modules) so the
// user gets immediate feedback instead of waiting on a 422 round-trip.

export const PROVIDERS = [
  { id: 'mtn', label: 'MTN MoMo', color: '#FBB01C' },
  { id: 'vodafone', label: 'Telecel Cash', color: '#E10A0A' },
  { id: 'airteltigo', label: 'AirtelTigo', color: '#FF6200' },
] as const;

export type ProviderId = (typeof PROVIDERS)[number]['id'];

const PATTERNS_BY_PROVIDER: Record<ProviderId, RegExp> = {
  mtn: /^0(24|25|53|54|55|59)\d{7}$/,
  vodafone: /^0(20|50)\d{7}$/,
  airteltigo: /^0(26|27|56|57)\d{7}$/,
};

/** Returns an error message if the number doesn't match the provider's known prefixes, else null. */
export function validateMomoNumber(provider: ProviderId, momoNumber: string): string | null {
  const trimmed = momoNumber.trim();
  if (!trimmed) {
    return 'Enter your MoMo number.';
  }
  if (!PATTERNS_BY_PROVIDER[provider].test(trimmed)) {
    const label = PROVIDERS.find(p => p.id === provider)?.label ?? provider;
    return `Doesn't look like a valid ${label} number.`;
  }
  return null;
}
