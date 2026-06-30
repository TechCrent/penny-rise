import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { TransactionBadge } from './TransactionBadge';
import type { WalletActivity } from '../../types/wallet';

interface Props {
  item: WalletActivity;
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleDateString('en-GH', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function ActivityRow({ item }: Props) {
  const isCredit = item.direction === 'CREDIT';
  const signPrefix = isCredit ? '+' : '-';

  return (
    <View style={styles.row} testID={`activity-row-${item.id}`}>
      <View style={styles.left}>
        <TransactionBadge type={item.transactionType} />
        <View style={styles.textGroup}>
          <Text style={styles.narrative} numberOfLines={1}>
            {item.counterparty ?? item.narrative}
          </Text>
          <Text style={styles.time}>{formatTime(item.createdAt)}</Text>
        </View>
      </View>

      <View style={styles.right}>
        <Text style={[styles.amount, isCredit ? styles.credit : styles.debit]}>
          {signPrefix} GHS {item.amountCedis}
        </Text>
        <Text style={styles.runningBalance}>Bal: {item.runningBalanceCedis}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
    backgroundColor: '#FFFFFF',
  },
  left: { flex: 1, flexDirection: 'column', gap: 6 },
  textGroup: { gap: 2 },
  narrative: { fontSize: 14, fontWeight: '500', color: '#111827' },
  time: { fontSize: 11, color: '#9CA3AF' },
  right: { alignItems: 'flex-end', marginLeft: 12 },
  amount: { fontSize: 15, fontWeight: '700' },
  credit: { color: '#065F46' },
  debit: { color: '#111827' },
  runningBalance: { fontSize: 11, color: '#9CA3AF', marginTop: 2 },
});
