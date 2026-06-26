import React from 'react';
import {
  View,
  Text,
  Modal,
  ScrollView,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTransactionDetail } from '../../../api/hooks/useTransactionDetail';

interface Props {
  reference: string | null;
  onClose: () => void;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('en-GH', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

const TYPE_LABELS: Record<string, string> = {
  DEPOSIT: 'Deposit',
  WITHDRAWAL: 'Withdrawal',
  TRANSFER: 'Transfer',
  REVERSAL: 'Reversal',
  VAULT_EARLY_EXIT_PENALTY: 'Early-exit penalty',
};

// Plain objects (not StyleSheet.create) — keys are accessed dynamically so the
// no-unused-styles rule would flag them inside StyleSheet.create.
const STATUS_PILL_BG: Record<string, { backgroundColor: string }> = {
  COMPLETED: { backgroundColor: '#05966922' },
  PENDING:   { backgroundColor: '#D9770622' },
  FAILED:    { backgroundColor: '#DC262622' },
  DEFAULT:   { backgroundColor: '#6B728022' },
};

const STATUS_TEXT_COLOR: Record<string, { color: string }> = {
  COMPLETED: { color: '#059669' },
  PENDING:   { color: '#D97706' },
  FAILED:    { color: '#DC2626' },
  DEFAULT:   { color: '#6B7280' },
};

const ENTRY_CREDIT = { dot: { backgroundColor: '#059669' }, amount: { color: '#059669' } };
const ENTRY_DEBIT  = { dot: { backgroundColor: '#DC2626' }, amount: { color: '#DC2626' } };

export function TransactionReceiptModal({ reference, onClose }: Props) {
  const { data, isLoading, error } = useTransactionDetail(reference);

  return (
    <Modal
      visible={!!reference}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={onClose}
    >
      <SafeAreaView style={styles.safe}>
        <View style={styles.header}>
          <Text style={styles.headerTitle}>Transaction receipt</Text>
          <TouchableOpacity
            onPress={onClose}
            style={styles.closeButton}
            accessibilityLabel="Close receipt"
            accessibilityRole="button"
          >
            <Text style={styles.closeIcon}>✕</Text>
          </TouchableOpacity>
        </View>

        {isLoading ? (
          <View style={styles.centered}>
            <ActivityIndicator size="large" color={INDIGO} />
          </View>
        ) : error ? (
          <View style={styles.centered}>
            <Text style={styles.errorText}>Could not load transaction details.</Text>
          </View>
        ) : data ? (
          <ScrollView contentContainerStyle={styles.receiptContent} showsVerticalScrollIndicator={false}>
            <View style={styles.amountHero}>
              <Text style={styles.heroLabel}>
                {TYPE_LABELS[data.transaction_type] ?? data.transaction_type}
              </Text>
              <Text style={styles.heroAmount}>GHS {data.gross_amount_cedis}</Text>
              <View style={[
                styles.statusPill,
                STATUS_PILL_BG[data.status] ?? STATUS_PILL_BG.DEFAULT,
              ]}>
                <Text style={[
                  styles.statusText,
                  STATUS_TEXT_COLOR[data.status] ?? STATUS_TEXT_COLOR.DEFAULT,
                ]}>
                  {data.status}
                </Text>
              </View>
            </View>

            <View style={styles.detailsCard}>
              <DetailRow label="Reference" value={data.transaction_reference} mono />
              <DetailRow label="Date" value={formatDate(data.created_at)} />
              {data.posted_at && (
                <DetailRow label="Settled" value={formatDate(data.posted_at)} />
              )}
              {data.external_reference && (
                <DetailRow label="Paystack ref" value={data.external_reference} mono />
              )}
              {data.narrative && (
                <DetailRow label="Narrative" value={data.narrative} />
              )}
            </View>

            {data.entries.length > 0 && (
              <View style={styles.entriesCard}>
                <Text style={styles.entriesTitle}>Ledger entries</Text>
                {data.entries.map((entry, i) => {
                  const isCredit = entry.direction === 'CREDIT';
                  const dirStyles = isCredit ? ENTRY_CREDIT : ENTRY_DEBIT;
                  return (
                    <View
                      key={i}
                      style={[styles.entryRow, i < data.entries.length - 1 && styles.entryBorder]}
                    >
                      <View style={[styles.directionDot, dirStyles.dot]} />
                      <View style={styles.entryInfo}>
                        <Text style={styles.entryAccountType}>{entry.account_type}</Text>
                        {entry.narrative && (
                          <Text style={styles.entryNarrative} numberOfLines={1}>
                            {entry.narrative}
                          </Text>
                        )}
                      </View>
                      <Text style={[styles.entryAmount, dirStyles.amount]}>
                        {isCredit ? '+' : '−'} GHS {entry.amount_cedis}
                      </Text>
                    </View>
                  );
                })}
              </View>
            )}
          </ScrollView>
        ) : null}
      </SafeAreaView>
    </Modal>
  );
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <View style={detailStyles.row}>
      <Text style={detailStyles.label}>{label}</Text>
      <Text style={[detailStyles.value, mono && detailStyles.mono]} selectable>
        {value}
      </Text>
    </View>
  );
}

const INDIGO = '#4F46E5';
const DARK   = '#1A1A2E';
const MUTED  = '#6B7280';

const styles = StyleSheet.create({
  safe:    { flex: 1, backgroundColor: '#F8F9FF' },
  header:  {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#EDEDF0',
    backgroundColor: '#FFFFFF',
  },
  headerTitle: { fontSize: 17, fontWeight: '700', color: DARK },
  closeButton: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  closeIcon:   { fontSize: 18, color: MUTED },
  centered:    { flex: 1, alignItems: 'center', justifyContent: 'center' },
  errorText:   { fontSize: 14, color: '#DC2626' },
  receiptContent: { padding: 16, paddingBottom: 40 },

  amountHero: { alignItems: 'center', paddingVertical: 28 },
  heroLabel: {
    fontSize: 13, color: MUTED, fontWeight: '600',
    textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 8,
  },
  heroAmount:  { fontSize: 36, fontWeight: '800', color: DARK, letterSpacing: -1, marginBottom: 12 },
  statusPill:  { borderRadius: 8, paddingHorizontal: 12, paddingVertical: 4 },
  statusText:  { fontSize: 12, fontWeight: '700', letterSpacing: 0.3 },

  detailsCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 4,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#EDEDF0',
  },
  entriesCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: '#EDEDF0',
  },
  entriesTitle: {
    fontSize: 12, fontWeight: '700', color: MUTED,
    textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 12,
  },
  entryRow:     { flexDirection: 'row', alignItems: 'center', paddingVertical: 10, gap: 10 },
  entryBorder:  { borderBottomWidth: 1, borderBottomColor: '#F3F4F6' },
  directionDot: { width: 8, height: 8, borderRadius: 4 },
  entryInfo:    { flex: 1 },
  entryAccountType: { fontSize: 13, fontWeight: '600', color: DARK },
  entryNarrative:   { fontSize: 12, color: MUTED, marginTop: 2 },
  entryAmount:      { fontSize: 14, fontWeight: '700' },
});

const detailStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  label: {
    fontSize: 12, color: MUTED, fontWeight: '600',
    textTransform: 'uppercase', letterSpacing: 0.4,
  },
  value: {
    fontSize: 13, color: DARK, fontWeight: '500',
    flex: 1, textAlign: 'right', marginLeft: 12,
  },
  mono: {
    fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace',
    fontSize: 11,
  },
});
