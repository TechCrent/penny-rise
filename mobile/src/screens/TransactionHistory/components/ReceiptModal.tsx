import React from 'react';
import {
  Modal,
  View,
  Text,
  Pressable,
  ScrollView,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { useTransactionDetail } from '../../../api/hooks/useTransactionDetail';
import type { UnifiedTransactionItem } from '../types';

interface Props {
  transaction: UnifiedTransactionItem | null;
  onClose: () => void;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('en-GH', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatCedis(pesewas: number): string {
  return (pesewas / 100).toFixed(2);
}

export function ReceiptModal({ transaction, onClose }: Props) {
  const isPending = transaction?.status === 'PENDING';

  // The list row (from the unified history endpoint) already carries
  // accountName/counterpartyName that GET /transactions/{ref} doesn't return
  // — so it's the primary data source. The live detail fetch only runs for
  // PENDING rows, to get a fresh status/fee reading without a round-trip on
  // rows that are already in a terminal state.
  const { data: liveDetail, isLoading: loadingLive } = useTransactionDetail(
    isPending ? transaction!.transactionReference : null,
  );

  if (!transaction) return null;

  const status = liveDetail?.status ?? transaction.status;

  return (
    <Modal
      visible={!!transaction}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={onClose}
      testID="receipt-modal"
    >
      <View style={styles.container}>
        <View style={styles.header}>
          <Text style={styles.title}>Transaction receipt</Text>
          <Pressable
            onPress={onClose}
            accessibilityRole="button"
            accessibilityLabel="Close receipt"
          >
            <Text style={styles.closeButton}>Done</Text>
          </Pressable>
        </View>

        {loadingLive ? (
          <ActivityIndicator style={styles.loader} testID="receipt-loading" />
        ) : (
          <ScrollView contentContainerStyle={styles.content}>
            <ReceiptRow
              label="Reference"
              value={transaction.transactionReference}
              testID="receipt-reference"
            />
            <ReceiptRow label="Type" value={transaction.transactionType} />
            <ReceiptRow
              label="Status"
              value={status}
              isPending={status === 'PENDING'}
              testID="receipt-status"
            />
            <ReceiptRow
              label="Amount"
              value={`GHS ${transaction.amountCedis}`}
              testID="receipt-amount"
            />
            {liveDetail && liveDetail.fee_amount_pesewas > 0 && (
              <ReceiptRow
                label="Fee"
                value={`GHS ${formatCedis(liveDetail.fee_amount_pesewas)}`}
                testID="receipt-fee"
              />
            )}
            <ReceiptRow label="Account" value={transaction.accountName} />
            {transaction.counterpartyName && (
              <ReceiptRow
                label="Counterparty"
                value={transaction.counterpartyName}
                testID="receipt-counterparty"
              />
            )}
            {transaction.narrative && (
              <ReceiptRow label="Narrative" value={transaction.narrative} />
            )}
            <ReceiptRow label="Date" value={formatDate(transaction.createdAt)} />
          </ScrollView>
        )}
      </View>
    </Modal>
  );
}

function ReceiptRow({
  label,
  value,
  isPending,
  testID,
}: {
  label: string;
  value: string;
  isPending?: boolean;
  testID?: string;
}) {
  return (
    <View style={rowStyles.row} testID={testID}>
      <Text style={rowStyles.label}>{label}</Text>
      <Text style={[rowStyles.value, isPending && rowStyles.pendingValue]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB' },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 16,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  title: { fontSize: 17, fontWeight: '700', color: '#111827' },
  closeButton: { fontSize: 15, color: '#1A1A1A', fontWeight: '600' },
  loader: { marginTop: 40 },
  content: { paddingHorizontal: 16, paddingTop: 8, paddingBottom: 32 },
});

const rowStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E5E7EB',
  },
  label: { fontSize: 13, color: '#6B7280' },
  value: { fontSize: 14, color: '#111827', flex: 1, textAlign: 'right', marginLeft: 12 },
  pendingValue: { color: '#D97706', fontWeight: '700' },
});
