import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import type { VaultListItem } from '../api/vaults';
import { PressableScale } from './ui';
import { colors, radii, spacing, typography } from '../theme';

interface VaultCardProps {
  vault: VaultListItem;
  onPress: () => void;
}

export function VaultCard({ vault, onPress }: VaultCardProps) {
  const isLocked = vault.vault_type === 'LOCKED';
  const isEarlyExit = vault.status === 'EARLY_EXIT_PENDING';
  const isFrozen = vault.status === 'FROZEN';
  const balanceUnavailable = vault.balance_pesewas === null;

  const unlockLabel = buildUnlockLabel(vault);
  const progress = buildProgress(vault);

  return (
    <PressableScale
      style={styles.card}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${vault.name} vault, ${vault.balance_cedis ?? 'balance unavailable'} GHS`}
    >
      <View style={styles.topRow}>
        <Text style={styles.vaultName} numberOfLines={1}>
          {vault.name}
        </Text>
        <View style={[styles.typeBadge, isLocked ? styles.badgeLocked : styles.badgeStandard]}>
          <Text style={[styles.badgeText, isLocked ? styles.badgeTextLocked : null]}>
            {isLocked ? 'LOCKED' : 'STANDARD'}
          </Text>
        </View>
      </View>

      <View style={styles.balanceRow}>
        {balanceUnavailable ? (
          <Text style={styles.balanceUnavailable}>—</Text>
        ) : (
          <>
            <Text style={styles.balanceCurrency}>GHS </Text>
            <Text style={styles.balanceAmount}>{vault.balance_cedis}</Text>
          </>
        )}
        {isEarlyExit && (
          <View style={styles.earlyExitPill}>
            <Text style={styles.earlyExitText}>EARLY EXIT</Text>
          </View>
        )}
        {isFrozen && (
          <View style={styles.frozenPill} testID={`vault-frozen-badge-${vault.id}`}>
            <Text style={styles.frozenText}>FROZEN</Text>
          </View>
        )}
      </View>

      {isFrozen && (
        <Text style={styles.frozenNotice}>
          Over your plan&apos;s limit — deposits, withdrawals, and unlock are blocked until you
          upgrade.
        </Text>
      )}

      {isLocked && unlockLabel ? <Text style={styles.unlockLabel}>{unlockLabel}</Text> : null}

      {progress !== null ? <ProgressTrack progress={progress} /> : null}
    </PressableScale>
  );
}

function ProgressTrack({ progress }: { progress: number }) {
  const width = useSharedValue(0);

  useEffect(() => {
    width.value = withTiming(Math.min(progress, 100), {
      duration: 700,
      easing: Easing.out(Easing.cubic),
    });
  }, [progress, width]);

  const animatedStyle = useAnimatedStyle(() => ({
    width: `${width.value}%`,
  }));

  return (
    <View style={styles.progressTrack}>
      <Animated.View style={[styles.progressFill, animatedStyle]} />
    </View>
  );
}

function buildUnlockLabel(vault: VaultListItem): string | null {
  if (vault.vault_type !== 'LOCKED') return null;

  if (vault.unlock_at && vault.unlock_amount) {
    const dateStr = formatDate(vault.unlock_at);
    const amtGhs = (vault.unlock_amount / 100).toFixed(2);
    const logic = vault.unlock_condition_logic === 'OR' ? 'or' : 'and';
    return `Unlocks on ${dateStr} ${logic} GHS ${amtGhs}`;
  }
  if (vault.unlock_at) {
    return `Unlocks on ${formatDate(vault.unlock_at)}`;
  }
  if (vault.unlock_amount) {
    return `Goal: GHS ${(vault.unlock_amount / 100).toFixed(2)}`;
  }
  return null;
}

function formatDate(isoDate: string): string {
  return new Date(isoDate).toLocaleDateString('en-GH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function buildProgress(vault: VaultListItem): number | null {
  if (!vault.unlock_amount || vault.balance_pesewas === null) return null;
  return (vault.balance_pesewas / vault.unlock_amount) * 100;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  vaultName: {
    ...typography.bodyMedium,
    color: colors.textPrimary,
    flex: 1,
    marginRight: spacing.sm,
  },
  typeBadge: {
    borderRadius: 6,
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
  },
  badgeLocked: {
    backgroundColor: colors.neutral[900],
  },
  badgeStandard: {
    backgroundColor: colors.neutral[100],
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    color: colors.neutral[700],
  },
  badgeTextLocked: {
    color: colors.neutral[0],
  },
  balanceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginBottom: spacing.xs,
    flexWrap: 'wrap',
    gap: spacing.xs,
  },
  balanceCurrency: {
    fontSize: 14,
    fontWeight: '500',
    color: colors.textSecondary,
  },
  balanceAmount: {
    ...typography.numericLarge,
    color: colors.textPrimary,
  },
  balanceUnavailable: {
    ...typography.numericLarge,
    color: colors.neutral[300],
  },
  earlyExitPill: {
    backgroundColor: colors.status.warningBg,
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
    marginLeft: spacing.xs,
    alignSelf: 'center',
  },
  earlyExitText: {
    fontSize: 10,
    fontWeight: '700',
    color: colors.status.warningText,
    letterSpacing: 0.4,
  },
  frozenPill: {
    backgroundColor: colors.status.infoBg,
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
    marginLeft: spacing.xs,
    alignSelf: 'center',
  },
  frozenText: {
    fontSize: 10,
    fontWeight: '700',
    color: colors.status.infoText,
    letterSpacing: 0.4,
  },
  frozenNotice: {
    fontSize: 12,
    color: colors.status.infoText,
    marginTop: spacing.xs,
    marginBottom: spacing.sm,
    lineHeight: 17,
  },
  unlockLabel: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: spacing.xs,
    marginBottom: spacing.sm,
  },
  progressTrack: {
    height: 4,
    backgroundColor: colors.neutral[100],
    borderRadius: 2,
    marginTop: spacing.sm,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: colors.gold.base,
    borderRadius: 2,
  },
});
