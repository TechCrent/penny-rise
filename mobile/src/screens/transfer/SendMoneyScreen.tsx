import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
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
      .catch(() => null);
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
              <ActivityIndicator testID="balance-loading" />
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
            keyboardType="decimal-pad"
            value={amountCedis}
            onChangeText={setAmountCedis}
            testID="amount-input"
          />

          <TextInput
            style={styles.noteInput}
            placeholder="Note (optional)"
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

          <TouchableOpacity
            style={[styles.submitBtn, !canSubmit && styles.submitBtnDisabled]}
            onPress={handleSend}
            disabled={!canSubmit}
            testID="submit-btn"
          >
            {submitting ? (
              <ActivityIndicator color="#FFFFFF" />
            ) : (
              <Text style={styles.submitBtnText}>Send GHS {amountCedis || '0.00'}</Text>
            )}
          </TouchableOpacity>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  flex1: { flex: 1 },
  content: { padding: 24 },
  heading: { fontSize: 24, fontWeight: '700', color: '#111827', marginBottom: 24 },
  recipientLabel: {
    fontSize: 12,
    color: '#6B7280',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  recipientName: { fontSize: 18, fontWeight: '600', color: '#111827', marginTop: 4 },
  recipientEmail: { fontSize: 14, color: '#6B7280', marginTop: 2, marginBottom: 20 },
  balanceRow: { marginBottom: 12 },
  balanceText: { fontSize: 14, color: '#374151' },
  balanceLow: { color: '#EF4444' },
  quotaRow: { marginBottom: 16 },
  freeTag: { fontSize: 13, color: '#10B981', fontWeight: '600' },
  feeText: { fontSize: 13, color: '#F59E0B', fontWeight: '600' },
  amountInput: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: '#E5E7EB',
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 20,
    fontWeight: '600',
    marginBottom: 12,
  },
  noteInput: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    borderWidth: 1.5,
    borderColor: '#E5E7EB',
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 15,
    marginBottom: 20,
  },
  insufficientText: { color: '#EF4444', fontSize: 13, marginBottom: 8 },
  errorText: { color: '#EF4444', fontSize: 13, marginBottom: 12, textAlign: 'center' },
  submitBtn: {
    backgroundColor: '#4F46E5',
    borderRadius: 14,
    paddingVertical: 16,
    alignItems: 'center',
  },
  submitBtnDisabled: { opacity: 0.5 },
  submitBtnText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
});
