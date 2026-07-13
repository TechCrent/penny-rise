import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { VaultListItem } from '../../../api/hooks/useVaults';
import { Icon, PressableScale } from '../../../components/ui';
import { colors, radii, shadows, spacing } from '../../../theme';

interface Props {
  vault: VaultListItem;
  onDeposit: () => void;
  onWithdraw: () => void;
  onEarlyExit: () => void;
  onCancelEarlyExit: () => void;
}

function isNaturallyUnlocked(vault: VaultListItem): boolean {
  if (vault.vault_type !== 'LOCKED') return false;

  const dateMet = vault.unlock_at ? new Date(vault.unlock_at) <= new Date() : false;
  const amountMet =
    vault.unlock_amount !== null ? (vault.balance_pesewas ?? 0) >= vault.unlock_amount : false;

  if (vault.unlock_at && vault.unlock_amount) {
    return vault.unlock_condition_logic === 'OR' ? dateMet || amountMet : dateMet && amountMet;
  }
  if (vault.unlock_at) return dateMet;
  if (vault.unlock_amount) return amountMet;
  return false;
}

export function VaultActionBar({
  vault,
  onDeposit,
  onWithdraw,
  onEarlyExit,
  onCancelEarlyExit,
}: Props) {
  const isEarlyExitPending = vault.status === 'EARLY_EXIT_PENDING';
  const isLocked = vault.vault_type === 'LOCKED';
  const unlocked = !isEarlyExitPending && isLocked && isNaturallyUnlocked(vault);

  if (isEarlyExitPending) {
    return (
      <View style={styles.container}>
        <View style={styles.coolOffBanner}>
          <Icon name="hourglass-outline" size={18} color={colors.status.warningText} />
          <View style={styles.coolOffBody}>
            <Text style={styles.coolOffTitle}>Early exit in progress</Text>
            <Text style={styles.coolOffSub}>72-hr cool-off in progress</Text>
          </View>
        </View>
        <View style={styles.singleRow}>
          <PressableScale
            style={[styles.button, styles.depositButton]}
            onPress={onDeposit}
            accessibilityRole="button"
            accessibilityLabel="Deposit"
          >
            <Text style={styles.buttonText}>Deposit</Text>
          </PressableScale>
          <PressableScale
            style={[styles.button, styles.destructiveButton]}
            onPress={onCancelEarlyExit}
            accessibilityRole="button"
            accessibilityLabel="Cancel early exit"
          >
            <Text style={styles.destructiveText}>Cancel exit</Text>
          </PressableScale>
        </View>
      </View>
    );
  }

  if (!isLocked || unlocked) {
    return (
      <View style={styles.container}>
        {unlocked && (
          <View style={styles.unlockedBanner}>
            <Icon name="lock-open-outline" size={14} color={colors.status.successText} />
            <Text style={styles.unlockedText}>Vault unlocked — withdraw freely, no penalty</Text>
          </View>
        )}
        <View style={styles.singleRow}>
          <PressableScale
            style={[styles.button, styles.depositButton]}
            onPress={onDeposit}
            accessibilityRole="button"
            accessibilityLabel="Deposit"
          >
            <Text style={styles.buttonText}>Deposit</Text>
          </PressableScale>
          <PressableScale
            style={[styles.button, styles.outlineButton]}
            onPress={onWithdraw}
            accessibilityRole="button"
            accessibilityLabel="Withdraw"
          >
            <Text style={styles.outlineText}>Withdraw</Text>
          </PressableScale>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.penaltyNotice}>
        <Icon name="warning-outline" size={13} color={colors.status.warningText} />
        <Text style={styles.penaltyText}>
          Early exit incurs a <Text style={styles.penaltyBold}>5% penalty</Text>
        </Text>
      </View>
      <View style={styles.singleRow}>
        <PressableScale
          style={[styles.button, styles.depositButton]}
          onPress={onDeposit}
          accessibilityRole="button"
          accessibilityLabel="Deposit"
        >
          <Text style={styles.buttonText}>Deposit</Text>
        </PressableScale>
        <PressableScale
          style={[styles.button, styles.outlineButton]}
          onPress={onEarlyExit}
          accessibilityRole="button"
          accessibilityLabel="Request early exit"
        >
          <Text style={styles.outlineText}>Early exit</Text>
        </PressableScale>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    paddingBottom: spacing.xs,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  singleRow: { flexDirection: 'row', gap: spacing.md },
  button: {
    flex: 1,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  depositButton: {
    backgroundColor: colors.gold.base,
    ...shadows.sm,
  },
  outlineButton: {
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
    backgroundColor: colors.surface,
  },
  destructiveButton: {
    borderWidth: 1.5,
    borderColor: colors.status.errorBorder,
    backgroundColor: colors.status.errorBg,
  },
  buttonText: { fontSize: 15, fontWeight: '700', color: colors.neutral[900] },
  outlineText: { fontSize: 15, fontWeight: '700', color: colors.textPrimary },
  destructiveText: { fontSize: 15, fontWeight: '700', color: colors.status.error },

  coolOffBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    backgroundColor: colors.status.warningBg,
    borderRadius: radii.sm,
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  coolOffBody: { flex: 1 },
  coolOffTitle: { fontSize: 13, fontWeight: '700', color: colors.status.warningText },
  coolOffSub: { fontSize: 12, color: colors.status.warningInkSoft, marginTop: 2 },

  unlockedBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: spacing.xs,
    backgroundColor: colors.status.successBg,
    borderRadius: radii.sm,
    padding: spacing.sm,
    marginBottom: spacing.md,
  },
  unlockedText: { fontSize: 13, fontWeight: '600', color: colors.status.successText },

  penaltyNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs,
    marginBottom: spacing.sm,
    justifyContent: 'center',
  },
  penaltyText: { fontSize: 12, color: colors.status.warningText },
  penaltyBold: { fontWeight: '700' },
});
