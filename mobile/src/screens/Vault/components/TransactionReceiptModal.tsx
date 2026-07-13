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
import { colors, radii, spacing, typography } from '../../../theme';

interface Props {
  reference: string | null;
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
  COMPLETED: { backgroundColor: colors.status.successBg },
  PENDING: { backgroundColor: colors.status.warningBg },
  FAILED: { backgroundColor: colors.status.errorBg },
  DEFAULT: { backgroundColor: colors.neutral[100] },
};

const STATUS_TEXT_COLOR: Record<string, { color: string }> = {
  COMPLETED: { color: colors.status.successText },
  PENDING: { color: colors.status.warningText },
  FAILED: { color: colors.status.errorText },
  DEFAULT: { color: colors.textSecondary },
};

const ENTRY_CREDIT = { dot: { backgroundColor: colors.status.success }, amount: { color: colors.status.successText } };
const ENTRY_DEBIT = { dot: { backgroundColor: colors.status.error }, amount: { color: colors.status.error } };

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
            <ActivityIndicator size="large" color={colors.gold.base} />
          </View>
        ) : error ? (
          <View style={styles.centered}>
            <Text style={styles.errorText}>Could not load transaction details.</Text>
          </View>
        ) : data ? (
          <ScrollView
            contentContainerStyle={styles.receiptContent}
            showsVerticalScrollIndicator={false}
          >
            <View style={styles.amountHero}>
              <Text style={styles.heroLabel}>
                {TYPE_LABELS[data.transaction_type] ?? data.transaction_type}
              </Text>
              <Text style={styles.heroAmount}>GHS {data.gross_amount_cedis}</Text>
              <View
                style={[styles.statusPill, STATUS_PILL_BG[data.status] ?? STATUS_PILL_BG.DEFAULT]}
              >
                <Text
                  style={[
                    styles.statusText,
                    STATUS_TEXT_COLOR[data.status] ?? STATUS_TEXT_COLOR.DEFAULT,
                  ]}
                >
                  {data.status}
                </Text>
              </View>
            </View>

            <View style={styles.detailsCard}>
              <DetailRow label="Reference" value={data.reference} mono />
              <DetailRow label="Date" value={formatDate(data.created_at)} />
              {data.completed_at && (
                <DetailRow label="Settled" value={formatDate(data.completed_at)} />
              )}
              {data.external_reference && (
                <DetailRow label="Paystack ref" value={data.external_reference} mono />
              )}
              {data.narrative && <DetailRow label="Narrative" value={data.narrative} />}
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

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
    backgroundColor: colors.surface,
  },
  headerTitle: { ...typography.h3, color: colors.textPrimary },
  closeButton: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  closeIcon: { fontSize: 18, color: colors.textSecondary },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  errorText: { fontSize: 14, color: colors.status.error },
  receiptContent: { padding: spacing.lg, paddingBottom: spacing['4xl'] },

  amountHero: { alignItems: 'center', paddingVertical: spacing['3xl'] },
  heroLabel: {
    fontSize: 13,
    color: colors.textSecondary,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: spacing.sm,
  },
  heroAmount: { fontSize: 36, fontWeight: '800', color: colors.textPrimary, letterSpacing: -1, marginBottom: spacing.md },
  statusPill: { borderRadius: radii.sm, paddingHorizontal: spacing.md, paddingVertical: 4 },
  statusText: { fontSize: 12, fontWeight: '700', letterSpacing: 0.3 },

  detailsCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: 4,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  entriesCard: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
  },
  entriesTitle: {
    fontSize: 12,
    fontWeight: '700',
    color: colors.textSecondary,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: spacing.md,
  },
  entryRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: spacing.sm, gap: spacing.sm },
  entryBorder: { borderBottomWidth: 1, borderBottomColor: colors.neutral[100] },
  directionDot: { width: 8, height: 8, borderRadius: 4 },
  entryInfo: { flex: 1 },
  entryAccountType: { fontSize: 13, fontWeight: '600', color: colors.textPrimary },
  entryNarrative: { fontSize: 12, color: colors.textSecondary, marginTop: 2 },
  entryAmount: { fontSize: 14, fontWeight: '700' },
});

const detailStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.neutral[100],
  },
  label: {
    fontSize: 12,
    color: colors.textSecondary,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.4,
  },
  value: {
    fontSize: 13,
    color: colors.textPrimary,
    fontWeight: '500',
    flex: 1,
    textAlign: 'right',
    marginLeft: spacing.md,
  },
  mono: {
    fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace',
    fontSize: 11,
  },
});
