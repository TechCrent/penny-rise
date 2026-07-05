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
import { susuApi } from '../../api/susu';
import { useWalletBalance } from '../../hooks/useWalletBalance';
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
              <Text style={styles.successIcon}>{'✓'}</Text>
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
                  <Text style={styles.closeX}>{'✕'}</Text>
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
                  <ActivityIndicator size="small" color="#6B7280" testID="balance-loading" />
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

              <TouchableOpacity
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
                  <ActivityIndicator size="small" color="#FFFFFF" />
                ) : (
                  <Text style={styles.confirmBtnText}>
                    {'Pay GHS '}
                    {group.contribution_amount_cedis}
                  </Text>
                )}
              </TouchableOpacity>
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
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 20,
    minHeight: SHEET_HEIGHT,
  },
  handle: {
    width: 40,
    height: 4,
    backgroundColor: '#E5E7EB',
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: 16,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  headerTitle: { fontSize: 18, fontWeight: '700', color: '#111827' },
  closeX: { fontSize: 18, color: '#6B7280' },
  amountRow: {
    backgroundColor: '#F9FAFB',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  amountLabel: { fontSize: 14, color: '#6B7280' },
  amountValue: { fontSize: 20, fontWeight: '800', color: '#111827' },
  balanceRow: { marginBottom: 16, minHeight: 20 },
  balanceText: { fontSize: 13, color: '#6B7280' },
  balanceLow: { color: '#EF4444' },
  errorText: { color: '#EF4444', fontSize: 13, marginBottom: 12, textAlign: 'center' },
  confirmBtn: {
    backgroundColor: '#111827',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 'auto',
  },
  confirmBtnDisabled: { opacity: 0.4 },
  confirmBtnText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  successContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
  },
  successIcon: { fontSize: 48, color: '#059669', marginBottom: 12 },
  successTitle: { fontSize: 22, fontWeight: '800', color: '#111827', marginBottom: 8 },
  successSub: { fontSize: 15, color: '#6B7280', textAlign: 'center' },
});
