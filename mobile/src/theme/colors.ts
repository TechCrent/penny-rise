/**
 * Brand palette. Kept flat + intentional: ~85% neutral, ~10% gold, ~5%
 * status. Gold is reserved for primary actions and highlighted values —
 * never used as a general decorative color.
 */

const neutral = {
  0: '#FFFFFF',
  50: '#F8FAFC',
  100: '#F1F5F9',
  200: '#E5E7EB',
  300: '#D1D5DB',
  400: '#9CA3AF',
  500: '#6B7280',
  600: '#4B5563',
  700: '#374151',
  800: '#1F2937',
  900: '#111827',
  950: '#0B1220',
} as const;

const gold = {
  light: '#FFF5CC',
  base: '#F5B700',
  hover: '#E8A600',
  // Darker shade for legible text/icons placed on light-gold backgrounds.
  text: '#8A6300',
} as const;

const status = {
  success: '#22C55E',
  successBg: '#ECFDF5',
  successText: '#065F46',
  error: '#EF4444',
  errorBg: '#FEF2F2',
  errorText: '#991B1B',
  // Softer red used for the outline of "danger" panels (early-exit /
  // penalty confirmations) — sits between errorBg and error in weight.
  errorBorder: '#FCA5A5',
  info: '#3B82F6',
  infoBg: '#EFF6FF',
  infoText: '#1E40AF',
  warning: '#F59E0B',
  warningBg: '#FFFBEB',
  warningText: '#92400E',
  // Amber ramp for the cool-off / early-exit warning panels. These were
  // duplicated as raw hex across the Vault screens; centralised here so the
  // whole family stays consistent.
  warningBorder: '#FDE68A',
  warningInk: '#78350F',
  warningInkSoft: '#B45309',
} as const;

export const colors = {
  neutral,
  gold,
  status,

  background: neutral[50],
  surface: neutral[0],
  surfaceRaised: neutral[0],

  textPrimary: neutral[900],
  textSecondary: neutral[500],
  textTertiary: neutral[400],
  textOnDark: neutral[0],
  textOnDarkMuted: 'rgba(255,255,255,0.64)',
  textOnDarkFaint: 'rgba(255,255,255,0.4)',

  border: neutral[200],
  borderStrong: neutral[300],

  // Hero / elevated dark surfaces (balance card, modals-on-dark, etc.)
  heroFrom: neutral[900],
  heroTo: neutral[950],
} as const;

export type Colors = typeof colors;
