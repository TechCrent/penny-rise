import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { TransactionBadge } from '../../../components/wallet/TransactionBadge';
import { PressableScale } from '../../../components/ui';
import { colors, radii, spacing } from '../../../theme';
import type { TransactionType } from '../../../types/wallet';
import type { UnifiedTransactionItem } from '../types';

interface Props {
  item: UnifiedTransactionItem;
  onPress: (item: UnifiedTransactionItem) => void;
}

// The unified history endpoint's transactionType values don't exactly match
// components/wallet/TransactionBadge's TransactionType union (e.g. "TRANSFER"
// vs "PEER_TRANSFER") — normalise so the same badge component can be reused
// instead of duplicating its label/colour table here.
function toBadgeType(raw: string): TransactionType {
  if (raw === 'TRANSFER') return 'PEER_TRANSFER';
  const known: TransactionType[] = [
    'DEPOSIT',
    'WITHDRAWAL',
    'SUSU_CONTRIBUTION',
    'SUSU_DISBURSEMENT',
    'VAULT_DEPOSIT',
    'VAULT_WITHDRAWAL',
    'EARLY_EXIT_PENALTY',
    'SUSU_PENALTY',
  ];
  return known.includes(raw as TransactionType) ? (raw as TransactionType) : 'OTHER';
}

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GH', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function TransactionListItem({ item, onPress }: Props) {
  const isPending = item.status === 'PENDING';
  const isFailed = item.status === 'FAILED';
  const isCredit = item.direction === 'IN';
  const amountColor = isCredit ? colors.status.successText : colors.textPrimary;
  const amountPrefix = isCredit ? '+' : '−';

  return (
    <PressableScale
      onPress={() => onPress(item)}
      style={styles.row}
      accessibilityRole="button"
      accessibilityLabel={`${item.accountName}, ${amountPrefix}GHS ${item.amountCedis}, ${item.status}`}
      testID={`transaction-row-${item.transactionReference}`}
    >
      <View style={styles.left}>
        <TransactionBadge type={toBadgeType(item.transactionType)} />
        <View style={styles.textGroup}>
          <Text style={styles.accountName} numberOfLines={1}>
            {item.accountName}
          </Text>
          {(item.counterpartyName || item.narrative) && (
            <Text style={styles.narrative} numberOfLines={1}>
              {item.counterpartyName ?? item.narrative}
            </Text>
          )}
          <View style={styles.badgeRow}>
            {isPending && (
              <View style={styles.pendingBadge} testID="pending-indicator">
                <Text style={styles.pendingLabel}>Pending</Text>
              </View>
            )}
            {isFailed && (
              <View style={styles.failedBadge} testID="failed-indicator">
                <Text style={styles.failedLabel}>Failed</Text>
              </View>
            )}
            <Text style={styles.timestamp}>{formatTimestamp(item.createdAt)}</Text>
          </View>
        </View>
      </View>

      <Text style={[styles.amount, { color: amountColor }]}>
        {amountPrefix}GHS {item.amountCedis}
      </Text>
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
  },
  left: { flex: 1, flexDirection: 'column', gap: spacing.sm, marginRight: spacing.md },
  textGroup: { gap: 2 },
  accountName: { fontSize: 14, fontWeight: '600', color: colors.textPrimary },
  narrative: { fontSize: 12, color: colors.textSecondary },
  badgeRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.xs, marginTop: 2 },
  pendingBadge: {
    backgroundColor: colors.status.warningBg,
    borderRadius: radii.sm,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  pendingLabel: { fontSize: 10, fontWeight: '700', color: colors.status.warningText },
  failedBadge: {
    backgroundColor: colors.status.errorBg,
    borderRadius: radii.sm,
    paddingHorizontal: 6,
    paddingVertical: 1,
  },
  failedLabel: { fontSize: 10, fontWeight: '700', color: colors.status.errorText },
  timestamp: { fontSize: 11, color: colors.textTertiary },
  amount: { fontSize: 15, fontWeight: '700' },
});
