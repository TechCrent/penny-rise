import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { RouteProp } from '@react-navigation/native';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { transferApi } from '../../api/transfers';
import type { TransferQuota } from '../../api/transfers';
import { useWalletBalance } from '../../hooks/useWalletBalance';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'SendMoney'>;
type Route = RouteProp<RootStackParamList, 'SendMoney'>;

export function SendMoneyScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { recipient } = route.params;
  const { balance, loading: balanceLoading, fetch: fetchBalance } = useWalletBalance();
  const [quota, setQuota] = useState<TransferQuota | null>(null);
  const [amountCedis, setAmountCedis] = useState('');
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const idempotencyKey = useRef(Math.random().toString(36).slice(2));

  useEffect(() => {
    fetchBalance();
    transferApi
      .getQuota()
      .then(setQuota)
      .catch(err => {
        console.error(err);
      });
  }, [fetchBalance]);

  const amountPesewas = Math.round(parseFloat(amountCedis || '0') * 100);
  const feePesewas = quota?.feeIfTransferNowPesewas ?? 0;
  const totalDebited = amountPesewas + feePesewas;
  const balancePesewas = balance?.balancePesewas ?? 0;
  const hasInsufficientBalance = amountPesewas > 0 && totalDebited > balancePesewas;
  const canSubmit = amountPesewas > 0 && !hasInsufficientBalance && !submitting;

  const handleSend = useCallback(async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await transferApi.send({
        recipientId: recipient.id,
        amountPesewas,
        note: note.trim() || undefined,
        idempotencyKey: idempotencyKey.current,
      });
      navigation.replace('TransferSuccess', { result, recipientName: recipient.displayName });
    } catch (e) {
      console.error(e);
      const err = e as { code?: string; message?: string } | null;
      const code = err?.code ?? '';
      if (code === 'TRANSFER_INSUFFICIENT_BALANCE') {
        setError('Insufficient balance for this transfer.');
      } else if (code === 'TRANSFER_RECIPIENT_NOT_FOUND') {
        setError('Recipient not found. Please try again.');
      } else {
        setError('Transfer failed. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }, [canSubmit, recipient, amountPesewas, note, navigation]);

  return (
    <SafeAreaView style={styles.screen}>
      <KeyboardAvoidingView
        style={styles.flex1}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.heading}>Send Money</Text>
          <Text style={styles.recipientLabel}>To</Text>
          <Text style={styles.recipientName} testID="recipient-name">
            {recipient.displayName}
          </Text>
          <Text style={styles.recipientEmail}>{recipient.email}</Text>

          <View style={styles.balanceRow}>
            {balanceLoading ? (
              <ActivityIndicator testID="balance-loading" color={colors.gold.base} />
            ) : (
              <Text
                style={[styles.balanceText, hasInsufficientBalance && styles.balanceLow]}
                testID="wallet-balance"
              >
                Available: GHS {balance?.balanceCedis ?? '—'}
              </Text>
            )}
          </View>

          {quota !== null && (
            <View style={styles.quotaRow} testID="quota-info">
              {quota.nextTransferIsFree ? (
                <Text style={styles.freeTag} testID="free-transfer-label">
                  Free transfer ({quota.freeTransfersRemaining} remaining)
                </Text>
              ) : (
                <Text style={styles.feeText} testID="fee-row">
                  Fee: GHS {quota.feeIfTransferNowCedis}
                </Text>
              )}
            </View>
          )}

          <TextInput
            style={styles.amountInput}
            placeholder="Amount (GHS)"
            placeholderTextColor={colors.textTertiary}
            keyboardType="decimal-pad"
            value={amountCedis}
            onChangeText={setAmountCedis}
            testID="amount-input"
          />

          <TextInput
            style={styles.noteInput}
            placeholder="Note (optional)"
            placeholderTextColor={colors.textTertiary}
            value={note}
            onChangeText={setNote}
            testID="note-input"
          />

          {hasInsufficientBalance && (
            <Text style={styles.insufficientText} testID="insufficient-balance">
              Insufficient balance
            </Text>
          )}

          {error !== null && (
            <Text style={styles.errorText} testID="submit-error">
              {error}
            </Text>
          )}

          <PressableScale
            style={[styles.submitBtn, !canSubmit && styles.submitBtnDisabled]}
            onPress={handleSend}
            disabled={!canSubmit}
            testID="submit-btn"
          >
            {submitting ? (
              <ActivityIndicator color={colors.neutral[900]} />
            ) : (
              <Text style={styles.submitBtnText}>Send GHS {amountCedis || '0.00'}</Text>
            )}
          </PressableScale>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  flex1: { flex: 1 },
  content: { padding: spacing.xl },
  heading: { ...typography.h1, color: colors.textPrimary, marginBottom: spacing['2xl'] },
  recipientLabel: {
    fontSize: 12,
    color: colors.textSecondary,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  recipientName: { fontSize: 18, fontWeight: '600', color: colors.textPrimary, marginTop: spacing.xs },
  recipientEmail: { fontSize: 14, color: colors.textSecondary, marginTop: 2, marginBottom: spacing.xl },
  balanceRow: { marginBottom: spacing.md },
  balanceText: { fontSize: 14, color: colors.neutral[700] },
  balanceLow: { color: colors.status.error },
  quotaRow: { marginBottom: spacing.lg },
  freeTag: { fontSize: 13, color: colors.status.success, fontWeight: '600' },
  feeText: { fontSize: 13, color: colors.status.warningText, fontWeight: '600' },
  amountInput: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    fontSize: 20,
    fontWeight: '600',
    color: colors.textPrimary,
    marginBottom: spacing.md,
  },
  noteInput: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    fontSize: 15,
    color: colors.textPrimary,
    marginBottom: spacing.xl,
  },
  insufficientText: { color: colors.status.error, fontSize: 13, marginBottom: spacing.sm },
  errorText: { color: colors.status.error, fontSize: 13, marginBottom: spacing.md, textAlign: 'center' },
  submitBtn: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: spacing.lg,
    alignItems: 'center',
  },
  submitBtnDisabled: { opacity: 0.5 },
  submitBtnText: { color: colors.neutral[900], fontSize: 16, fontWeight: '700' },
});
