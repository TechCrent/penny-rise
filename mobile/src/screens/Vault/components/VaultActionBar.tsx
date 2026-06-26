import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { VaultListItem } from '../../../api/hooks/useVaults';

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
          <Text style={styles.coolOffIcon}>⏳</Text>
          <View style={styles.coolOffBody}>
            <Text style={styles.coolOffTitle}>Early exit in progress</Text>
            <Text style={styles.coolOffSub}>72-hr cool-off in progress</Text>
          </View>
        </View>
        <View style={styles.singleRow}>
          <TouchableOpacity
            style={[styles.button, styles.depositButton]}
            onPress={onDeposit}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Deposit"
          >
            <Text style={styles.buttonText}>Deposit</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.button, styles.destructiveButton]}
            onPress={onCancelEarlyExit}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Cancel early exit"
          >
            <Text style={styles.destructiveText}>Cancel exit</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  if (!isLocked || unlocked) {
    return (
      <View style={styles.container}>
        {unlocked && (
          <View style={styles.unlockedBanner}>
            <Text style={styles.unlockedText}>🔓 Vault unlocked — withdraw freely, no penalty</Text>
          </View>
        )}
        <View style={styles.singleRow}>
          <TouchableOpacity
            style={[styles.button, styles.depositButton]}
            onPress={onDeposit}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Deposit"
          >
            <Text style={styles.buttonText}>Deposit</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.button, styles.outlineButton]}
            onPress={onWithdraw}
            activeOpacity={0.85}
            accessibilityRole="button"
            accessibilityLabel="Withdraw"
          >
            <Text style={styles.outlineText}>Withdraw</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.penaltyNotice}>
        <Text style={styles.penaltyIcon}>⚠️</Text>
        <Text style={styles.penaltyText}>
          Early exit incurs a <Text style={styles.penaltyBold}>5% penalty</Text>
        </Text>
      </View>
      <View style={styles.singleRow}>
        <TouchableOpacity
          style={[styles.button, styles.depositButton]}
          onPress={onDeposit}
          activeOpacity={0.85}
          accessibilityRole="button"
          accessibilityLabel="Deposit"
        >
          <Text style={styles.buttonText}>Deposit</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.button, styles.outlineButton]}
          onPress={onEarlyExit}
          activeOpacity={0.85}
          accessibilityRole="button"
          accessibilityLabel="Request early exit"
        >
          <Text style={styles.outlineText}>Early exit</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const INDIGO = '#4F46E5';
const DARK = '#1A1A2E';
const AMBER = '#D97706';

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 4,
    backgroundColor: '#FFFFFF',
    borderTopWidth: 1,
    borderTopColor: '#EDEDF0',
  },
  singleRow: { flexDirection: 'row', gap: 12 },
  button: {
    flex: 1,
    borderRadius: 13,
    paddingVertical: 14,
    alignItems: 'center',
  },
  depositButton: {
    backgroundColor: INDIGO,
    shadowColor: INDIGO,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.22,
    shadowRadius: 6,
    elevation: 3,
  },
  outlineButton: {
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
    backgroundColor: '#FFFFFF',
  },
  destructiveButton: {
    borderWidth: 1.5,
    borderColor: '#FCA5A5',
    backgroundColor: '#FEF2F2',
  },
  buttonText: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
  outlineText: { fontSize: 15, fontWeight: '700', color: DARK },
  destructiveText: { fontSize: 15, fontWeight: '700', color: '#DC2626' },

  coolOffBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: '#FEF3C7',
    borderRadius: 10,
    padding: 12,
    marginBottom: 12,
  },
  coolOffIcon: { fontSize: 20 },
  coolOffBody: { flex: 1 },
  coolOffTitle: { fontSize: 13, fontWeight: '700', color: '#92400E' },
  coolOffSub: { fontSize: 12, color: '#B45309', marginTop: 2 },

  unlockedBanner: {
    backgroundColor: '#ECFDF5',
    borderRadius: 10,
    padding: 10,
    marginBottom: 12,
    alignItems: 'center',
  },
  unlockedText: { fontSize: 13, fontWeight: '600', color: '#065F46' },

  penaltyNotice: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 10,
    justifyContent: 'center',
  },
  penaltyIcon: { fontSize: 13 },
  penaltyText: { fontSize: 12, color: AMBER },
  penaltyBold: { fontWeight: '700' },
});
