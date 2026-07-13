import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Modal,
  Animated,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { susuApi } from '../../api/susu';
import { useWalletBalance } from '../../hooks/useWalletBalance';
import { PressableScale } from '../ui';
import { colors, radii, spacing, typography } from '../../theme';
import type { SusuGroupDetailResponse } from '../../types/susu';

const SHEET_HEIGHT = 400;

type Props = {
  visible: boolean;
  group: SusuGroupDetailResponse;
  onClose: () => void;
  onSuccess: () => void;
};

export function ContributeBottomSheet({ visible, group, onClose, onSuccess }: Props) {
  const slideAnim = useRef(new Animated.Value(SHEET_HEIGHT)).current;
  const idempotencyKey = useRef('');

  const [confirming, setConfirming] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [succeeded, setSucceeded] = useState(false);

  const { balance, loading: balanceLoading, fetch: fetchBalance } = useWalletBalance();

  useEffect(() => {
    if (visible) {
      idempotencyKey.current = `contrib-${group.current_round?.id ?? 'unknown'}-${Date.now()}`;
      setSubmitError(null);
      setSucceeded(false);
      fetchBalance();
      Animated.timing(slideAnim, {
        toValue: 0,
        duration: 300,
        useNativeDriver: true,
      }).start();
    } else {
      Animated.timing(slideAnim, {
        toValue: SHEET_HEIGHT,
        duration: 200,
        useNativeDriver: true,
      }).start();
    }
  }, [visible, fetchBalance, slideAnim, group.current_round?.id]);

  const contributionPesewas = group.contribution_amount;
  const hasSufficientBalance = balance !== null && balance.balancePesewas >= contributionPesewas;
  const isInsufficientBalance = balance !== null && balance.balancePesewas < contributionPesewas;

  const handleConfirm = useCallback(async () => {
    const round = group.current_round;
    if (!round || isInsufficientBalance) return;

    setConfirming(true);
    setSubmitError(null);
    try {
      await susuApi.payContribution(round.id, idempotencyKey.current);
      setSucceeded(true);
      setTimeout(() => {
        onSuccess();
      }, 1200);
    } catch (e) {
      console.error(e);
      const err = e as { message?: string } | null;
      setSubmitError(err?.message ?? 'Payment failed. Try again.');
    } finally {
      setConfirming(false);
    }
  }, [group, isInsufficientBalance, onSuccess]);

  const sheetStyle = { transform: [{ translateY: slideAnim }] };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onClose}
      statusBarTranslucent
    >
      <View style={styles.scrim}>
        <Animated.View style={[styles.sheet, sheetStyle]} testID="contribute-sheet">
          {succeeded ? (
            <View testID="success-state" style={styles.successContainer}>
              <View style={styles.successIconBadge}>
                <Ionicons name="checkmark-circle" size={40} color={colors.status.success} />
              </View>
              <Text style={styles.successTitle}>Payment sent!</Text>
              <Text style={styles.successSub}>
                {'GHS '}
                {group.contribution_amount_cedis}
                {' contributed successfully.'}
              </Text>
            </View>
          ) : (
            <>
              <View style={styles.handle} />

              <View style={styles.header}>
                <Text style={styles.headerTitle}>Pay contribution</Text>
                <TouchableOpacity
                  onPress={onClose}
                  testID="close-btn"
                  hitSlop={{ top: 12, right: 12, bottom: 12, left: 12 }}
                >
                  <Ionicons name="close" size={20} color={colors.textSecondary} />
                </TouchableOpacity>
              </View>

              <View style={styles.amountRow}>
                <Text style={styles.amountLabel}>Amount due</Text>
                <Text style={styles.amountValue} testID="contribution-amount">
                  {'GHS '}
                  {group.contribution_amount_cedis}
                </Text>
              </View>

              <View style={styles.balanceRow} testID="wallet-balance-row">
                {balanceLoading ? (
                  <ActivityIndicator size="small" color={colors.textSecondary} testID="balance-loading" />
                ) : (
                  <Text
                    style={[styles.balanceText, isInsufficientBalance && styles.balanceLow]}
                    testID="wallet-balance"
                  >
                    {'Wallet · GHS '}
                    {balance?.balanceCedis ?? '—'}
                    {isInsufficientBalance && ' (low)'}
                  </Text>
                )}
              </View>

              {submitError !== null && (
                <Text style={styles.errorText} testID="submit-error">
                  {submitError}
                </Text>
              )}

              <PressableScale
                style={[
                  styles.confirmBtn,
                  (confirming ||
                    isInsufficientBalance ||
                    (!hasSufficientBalance && !balanceLoading)) &&
                    styles.confirmBtnDisabled,
                ]}
                onPress={handleConfirm}
                disabled={confirming || isInsufficientBalance || (!balance && !balanceLoading)}
                testID="confirm-btn"
              >
                {confirming ? (
                  <ActivityIndicator size="small" color={colors.neutral[900]} />
                ) : (
                  <Text style={styles.confirmBtnText}>
                    {'Pay GHS '}
                    {group.contribution_amount_cedis}
                  </Text>
                )}
              </PressableScale>
            </>
          )}
        </Animated.View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  scrim: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  sheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radii['2xl'],
    borderTopRightRadius: radii['2xl'],
    padding: spacing.xl,
    minHeight: SHEET_HEIGHT,
  },
  handle: {
    width: 40,
    height: 4,
    backgroundColor: colors.neutral[200],
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: spacing.lg,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing['2xl'],
  },
  headerTitle: { ...typography.h3, color: colors.textPrimary },
  amountRow: {
    backgroundColor: colors.neutral[50],
    borderRadius: radii.md,
    padding: spacing.lg,
    marginBottom: spacing.md,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  amountLabel: { fontSize: 14, color: colors.textSecondary },
  amountValue: { fontSize: 20, fontWeight: '800', color: colors.textPrimary },
  balanceRow: { marginBottom: spacing.lg, minHeight: 20 },
  balanceText: { fontSize: 13, color: colors.textSecondary },
  balanceLow: { color: colors.status.error },
  errorText: { color: colors.status.error, fontSize: 13, marginBottom: spacing.md, textAlign: 'center' },
  confirmBtn: {
    backgroundColor: colors.gold.base,
    paddingVertical: spacing.lg,
    borderRadius: radii.md,
    alignItems: 'center',
    marginTop: 'auto',
  },
  confirmBtnDisabled: { opacity: 0.4 },
  confirmBtnText: { color: colors.neutral[900], fontSize: 16, fontWeight: '700' },
  successContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: spacing['4xl'],
  },
  successIconBadge: {
    width: 76,
    height: 76,
    borderRadius: radii.pill,
    backgroundColor: colors.status.successBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
  },
  successTitle: { fontSize: 22, fontWeight: '800', color: colors.textPrimary, marginBottom: spacing.sm },
  successSub: { fontSize: 15, color: colors.textSecondary, textAlign: 'center' },
});
