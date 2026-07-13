import type { TextStyle } from 'react-native';

/**
 * Type scale. No custom font is bundled (adding one is a separate,
 * deliberate decision involving asset licensing/loading) — this scale uses
 * the OS system font (San Francisco / Roboto) at weights that still read as
 * premium: heavier headlines, restrained body weight, tabular numerals for
 * money so digits don't jitter in place as balances update.
 */
export const typography = {
  display: {
    fontSize: 34,
    lineHeight: 40,
    fontWeight: '800',
    letterSpacing: -0.4,
  } as TextStyle,
  h1: {
    fontSize: 28,
    lineHeight: 34,
    fontWeight: '800',
    letterSpacing: -0.3,
  } as TextStyle,
  h2: {
    fontSize: 22,
    lineHeight: 28,
    fontWeight: '700',
    letterSpacing: -0.2,
  } as TextStyle,
  h3: {
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '700',
  } as TextStyle,
  body: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '400',
  } as TextStyle,
  bodyMedium: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600',
  } as TextStyle,
  caption: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '500',
  } as TextStyle,
  label: {
    fontSize: 11,
    lineHeight: 14,
    fontWeight: '700',
    letterSpacing: 0.6,
    textTransform: 'uppercase',
  } as TextStyle,
  button: {
    fontSize: 16,
    lineHeight: 20,
    fontWeight: '700',
  } as TextStyle,
  numericHero: {
    fontSize: 38,
    lineHeight: 44,
    fontWeight: '800',
    letterSpacing: -0.6,
    fontVariant: ['tabular-nums'],
  } as TextStyle,
  numericLarge: {
    fontSize: 24,
    lineHeight: 30,
    fontWeight: '800',
    fontVariant: ['tabular-nums'],
  } as TextStyle,
  numericMedium: {
    fontSize: 17,
    lineHeight: 22,
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  } as TextStyle,
} as const;

export type TypographyKey = keyof typeof typography;
