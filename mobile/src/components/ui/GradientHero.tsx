import React from 'react';
import { View, Text, Image, StyleSheet } from 'react-native';
import type { StyleProp, ViewStyle } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { Icon, type IconName } from './Icon';
import { colors, radii, shadows, spacing, typography } from '../../theme';

interface GradientHeroProps {
  title: string;
  subtitle?: string;
  /** Show the PennyRise logo badge (mutually exclusive with `icon`). */
  showLogo?: boolean;
  /** Show a gold icon badge instead of the logo. */
  icon?: IconName;
  style?: StyleProp<ViewStyle>;
  children?: React.ReactNode;
}

/**
 * The signature premium header: a dark gold-accented gradient card with an
 * optional gold badge, white title, and muted subtitle. Matches the app's
 * hero-card language (balance cards) and is reused across entry, form, and
 * confirmation screens for a consistent branded top.
 */
export function GradientHero({
  title,
  subtitle,
  showLogo,
  icon,
  style,
  children,
}: GradientHeroProps) {
  return (
    <LinearGradient
      colors={[colors.heroFrom, colors.heroTo]}
      start={{ x: 0, y: 0 }}
      end={{ x: 1, y: 1 }}
      style={[styles.hero, style]}
    >
      {showLogo ? (
        <View style={styles.badge}>
          <Image
            source={require('../../../assets/images/splash-icon.png')}
            style={styles.logo}
            resizeMode="contain"
          />
        </View>
      ) : icon ? (
        <View style={styles.badge}>
          <Icon name={icon} size={30} color={colors.neutral[900]} />
        </View>
      ) : null}
      <Text style={styles.title}>{title}</Text>
      {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      {children}
    </LinearGradient>
  );
}

const styles = StyleSheet.create({
  hero: {
    borderRadius: radii['2xl'],
    paddingVertical: spacing['3xl'],
    paddingHorizontal: spacing.xl,
    marginBottom: spacing.xl,
    ...shadows.lg,
  },
  badge: {
    width: 64,
    height: 64,
    borderRadius: radii.xl,
    backgroundColor: colors.gold.base,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
    ...shadows.md,
  },
  logo: { width: 38, height: 38, tintColor: colors.neutral[900] },
  title: { ...typography.h1, color: colors.textOnDark, marginBottom: spacing.xs },
  subtitle: { fontSize: 15, color: colors.textOnDarkMuted },
});
