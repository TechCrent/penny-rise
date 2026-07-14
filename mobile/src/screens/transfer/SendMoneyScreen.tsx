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
import Animated from 'react-native-reanimated';
import type { RootStackParamList } from '../../navigation/RootNavigator';
import { transferApi } from '../../api/transfers';
import type { TransferQuota } from '../../api/transfers';
import { useWalletBalance } from '../../hooks/useWalletBalance';
import { PrimaryButton } from '../../components/PrimaryButton';
import { FormField } from '../../components/FormField';
import { Banner, GradientHero, ScreenHeader, fadeInUp } from '../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'SendMoney'>;
type Route = RouteProp<RootStackParamList, 'SendMoney'>;

function initialOf(name: string): string {
  return name.trim().charAt(0).toUpperCase() || '?';
}

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
        <ScrollView
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <ScreenHeader onBack={() => navigation.goBack()} />

          <Animated.View entering={fadeInUp(40)}>
            <GradientHero
              icon="send-outline"
              title="Send Money"
              subtitle="Review the details and confirm your transfer."
            />
          </Animated.View>

          <Animated.View entering={fadeInUp(80)} style={styles.card}>
            <Text style={styles.recipientLabel}>To</Text>
            <View style={styles.recipientRow}>
              <View style={styles.avatar}>
                <Text style={styles.avatarText}>{initialOf(recipient.displayName)}</Text>
              </View>
              <View style={styles.recipientBody}>
                <Text style={styles.recipientName} testID="recipient-name">
                  {recipient.displayName}
                </Text>
                <Text style={styles.recipientEmail}>{recipient.email}</Text>
              </View>
            </View>
          </Animated.View>

          <Animated.View entering={fadeInUp(120)} style={styles.card}>
            <Text style={styles.fieldLabel}>Amount</Text>
            <View style={styles.amountRow}>
              <Text style={styles.currencyPrefix}>GHS</Text>
              <TextInput
                style={styles.amountInput}
                placeholder="0.00"
                placeholderTextColor={colors.textTertiary}
                keyboardType="decimal-pad"
                value={amountCedis}
                onChangeText={setAmountCedis}
                testID="amount-input"
              />
            </View>

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
          </Animated.View>

          <Animated.View entering={fadeInUp(160)} style={styles.card}>
            <FormField
              label="Note (optional)"
              placeholder="Note (optional)"
              value={note}
              onChangeText={setNote}
              testID="note-input"
            />
          </Animated.View>

          {hasInsufficientBalance && (
            <Banner tone="error" message="Insufficient balance" testID="insufficient-balance" />
          )}

          {error !== null && <Banner tone="error" message={error} testID="submit-error" />}

          <Animated.View entering={fadeInUp(200)}>
            <PrimaryButton
              title={`Send GHS ${amountCedis || '0.00'}`}
              onPress={handleSend}
              loading={submitting}
              disabled={!canSubmit}
              testID="submit-btn"
            />
          </Animated.View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  flex1: { flex: 1 },
  content: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    marginBottom: spacing.lg,
    ...shadows.sm,
  },
  recipientLabel: {
    ...typography.label,
    color: colors.textSecondary,
    marginBottom: spacing.sm,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  recipientRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadows.sm,
  },
  avatarText: { ...typography.bodyMedium, color: colors.gold.text, fontWeight: '700' },
  recipientBody: { flex: 1 },
  recipientName: { ...typography.bodyMedium, color: colors.textPrimary, fontWeight: '700' },
  recipientEmail: { ...typography.caption, color: colors.textSecondary, marginTop: 2 },
  fieldLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: colors.neutral[700],
    marginBottom: spacing.sm,
  },
  amountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    borderWidth: 1.5,
    borderColor: colors.border,
    borderRadius: radii.lg,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    marginBottom: spacing.lg,
    ...shadows.sm,
  },
  currencyPrefix: { ...typography.numericLarge, color: colors.textTertiary },
  amountInput: {
    flex: 1,
    ...typography.numericHero,
    color: colors.textPrimary,
    padding: 0,
  },
  balanceRow: { marginBottom: spacing.sm },
  balanceText: { ...typography.caption, color: colors.neutral[700] },
  balanceLow: { color: colors.status.error },
  quotaRow: {
    marginTop: spacing.xs,
    paddingTop: spacing.sm,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: colors.border,
  },
  freeTag: { ...typography.caption, color: colors.status.success, fontWeight: '600' },
  feeText: { ...typography.caption, color: colors.status.warningText, fontWeight: '600' },
});
