import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import type { VaultListItem } from '../api/vaults';

interface VaultCardProps {
  vault: VaultListItem;
  onPress: () => void;
}

export function VaultCard({ vault, onPress }: VaultCardProps) {
  const isLocked = vault.vault_type === 'LOCKED';
  const isEarlyExit = vault.status === 'EARLY_EXIT_PENDING';
  const balanceUnavailable = vault.balance_pesewas === null;

  const unlockLabel = buildUnlockLabel(vault);
  const progress = buildProgress(vault);

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      activeOpacity={0.85}
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
      </View>

      {isLocked && unlockLabel ? <Text style={styles.unlockLabel}>{unlockLabel}</Text> : null}

      {progress !== null ? (
        <View style={styles.progressTrack}>
          <View style={[styles.progressFill, { width: `${Math.min(progress, 100)}%` }]} />
        </View>
      ) : null}
    </TouchableOpacity>
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
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 18,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  vaultName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#111827',
    flex: 1,
    marginRight: 8,
  },
  typeBadge: {
    borderRadius: 6,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  badgeLocked: {
    backgroundColor: '#1A1A1A',
  },
  badgeStandard: {
    backgroundColor: '#F3F4F6',
  },
  badgeText: {
    fontSize: 10,
    fontWeight: '700',
    letterSpacing: 0.5,
    color: '#374151',
  },
  badgeTextLocked: {
    color: '#FFFFFF',
  },
  balanceRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    marginBottom: 6,
    flexWrap: 'wrap',
    gap: 4,
  },
  balanceCurrency: {
    fontSize: 14,
    fontWeight: '500',
    color: '#6B7280',
  },
  balanceAmount: {
    fontSize: 22,
    fontWeight: '700',
    color: '#111827',
  },
  balanceUnavailable: {
    fontSize: 22,
    fontWeight: '700',
    color: '#D1D5DB',
  },
  earlyExitPill: {
    backgroundColor: '#FEF3C7',
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
    marginLeft: 6,
    alignSelf: 'center',
  },
  earlyExitText: {
    fontSize: 10,
    fontWeight: '700',
    color: '#92400E',
    letterSpacing: 0.4,
  },
  unlockLabel: {
    fontSize: 12,
    color: '#6B7280',
    marginTop: 4,
    marginBottom: 8,
  },
  progressTrack: {
    height: 4,
    backgroundColor: '#F3F4F6',
    borderRadius: 2,
    marginTop: 8,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#1A1A1A',
    borderRadius: 2,
  },
});
