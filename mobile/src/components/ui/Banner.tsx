import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Icon, type IconName } from './Icon';
import { colors, radii, spacing } from '../../theme';

export type BannerTone = 'success' | 'info' | 'error' | 'warning';

const TONES: Record<BannerTone, { bg: string; accent: string; text: string; icon: IconName }> = {
  success: {
    bg: colors.status.successBg,
    accent: colors.status.success,
    text: colors.status.successText,
    icon: 'checkmark-circle',
  },
  info: {
    bg: colors.status.infoBg,
    accent: colors.status.info,
    text: colors.status.infoText,
    icon: 'time-outline',
  },
  error: {
    bg: colors.status.errorBg,
    accent: colors.status.error,
    text: colors.status.errorText,
    icon: 'warning-outline',
  },
  warning: {
    bg: colors.status.warningBg,
    accent: colors.status.warning,
    text: colors.status.warningText,
    icon: 'warning-outline',
  },
};

interface BannerProps {
  tone: BannerTone;
  /** Body copy. */
  message: string;
  /** Optional bold line above the message. */
  title?: string;
  /** Overrides the tone's default icon. */
  icon?: IconName;
  testID?: string;
}

/**
 * Status banner with an icon and a colored left accent bar. Shared across
 * auth, KYC, and money-movement screens so success/info/error/warning
 * messaging reads identically everywhere.
 */
export function Banner({ tone, message, title, icon, testID }: BannerProps) {
  const t = TONES[tone];
  return (
    <View
      style={[styles.container, { backgroundColor: t.bg, borderLeftColor: t.accent }]}
      testID={testID}
    >
      <View style={styles.iconWrap}>
        <Icon name={icon ?? t.icon} size={16} color={t.accent} />
      </View>
      <View style={styles.body}>
        {title ? <Text style={[styles.title, { color: t.text }]}>{title}</Text> : null}
        <Text style={[styles.message, { color: t.text }]}>{message}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.sm,
    borderRadius: radii.md,
    borderLeftWidth: 3,
    padding: spacing.md,
    marginBottom: spacing.lg,
  },
  iconWrap: { marginTop: 1 },
  body: { flex: 1 },
  title: { fontSize: 14, fontWeight: '700', marginBottom: 2 },
  message: { fontSize: 14, lineHeight: 19, fontWeight: '500' },
});
