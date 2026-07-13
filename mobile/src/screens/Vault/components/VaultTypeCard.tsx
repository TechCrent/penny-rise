import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Icon, PressableScale } from '../../../components/ui';
import { colors, radii, spacing, typography } from '../../../theme';

type VaultType = 'STANDARD' | 'LOCKED';

interface VaultTypeCardProps {
  type: VaultType;
  selected: boolean;
  onSelect: () => void;
  disabled?: boolean;
  limitReached?: boolean;
}

const TYPE_META = {
  STANDARD: {
    icon: 'wallet-outline' as const,
    title: 'Standard vault',
    desc: 'Save freely. Deposit and withdraw anytime with no restrictions.',
    note: null,
  },
  LOCKED: {
    icon: 'lock-closed-outline' as const,
    title: 'Locked vault',
    desc: 'Commit to a goal. Lock your savings until a date or amount target is reached.',
    note: 'Early exit incurs a 5% penalty on the balance at the time of exit.',
  },
} as const;

export function VaultTypeCard({
  type,
  selected,
  onSelect,
  disabled,
  limitReached,
}: VaultTypeCardProps) {
  const meta = TYPE_META[type];

  return (
    <PressableScale
      style={[styles.card, selected && styles.cardSelected, disabled && styles.cardDisabled]}
      onPress={onSelect}
      disabled={disabled}
      accessibilityRole="radio"
      accessibilityState={{ selected, disabled }}
      accessibilityLabel={`${meta.title}. ${meta.desc}`}
    >
      <View style={styles.topRow}>
        <View style={styles.iconWrap}>
          <Icon name={meta.icon} size={20} color={colors.gold.text} />
        </View>
        <View style={[styles.radioOuter, selected && styles.radioOuterSelected]}>
          {selected && <View style={styles.radioInner} />}
        </View>
      </View>

      <Text style={[styles.title, disabled && styles.textDisabled]}>{meta.title}</Text>
      <Text style={[styles.desc, disabled && styles.textDisabled]}>{meta.desc}</Text>

      {type === 'LOCKED' && meta.note && (
        <View style={styles.penaltyRow}>
          <Icon name="warning-outline" size={13} color={colors.status.warningText} />
          <Text style={styles.penaltyText}>{meta.note}</Text>
        </View>
      )}

      {limitReached && (
        <View style={styles.limitBanner}>
          <Text style={styles.limitText}>
            {type === 'STANDARD'
              ? 'Free plan: 2 standard vaults maximum'
              : 'Free plan: 1 locked vault maximum'}
          </Text>
        </View>
      )}
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
    borderWidth: 2,
    borderColor: colors.border,
  },
  cardSelected: {
    borderColor: colors.gold.base,
    backgroundColor: colors.gold.light,
  },
  cardDisabled: {
    opacity: 0.55,
    backgroundColor: colors.neutral[50],
  },
  topRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: radii.pill,
    backgroundColor: colors.neutral[0],
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioOuter: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 2,
    borderColor: colors.borderStrong,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioOuterSelected: { borderColor: colors.gold.base },
  radioInner: {
    width: 11,
    height: 11,
    borderRadius: 6,
    backgroundColor: colors.gold.base,
  },
  title: {
    ...typography.bodyMedium,
    color: colors.textPrimary,
    marginBottom: spacing.xxs,
  },
  desc: {
    fontSize: 13,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  textDisabled: {
    color: colors.textTertiary,
  },
  penaltyRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: spacing.xs,
    marginTop: spacing.sm,
    backgroundColor: colors.status.warningBg,
    borderRadius: radii.sm,
    padding: spacing.sm,
  },
  penaltyText: {
    flex: 1,
    fontSize: 12,
    color: colors.status.warningText,
    fontWeight: '600',
    lineHeight: 17,
  },
  limitBanner: {
    marginTop: spacing.sm,
    backgroundColor: colors.status.errorBg,
    borderRadius: radii.sm,
    padding: spacing.sm,
  },
  limitText: {
    fontSize: 12,
    color: colors.status.errorText,
    fontWeight: '600',
  },
});
