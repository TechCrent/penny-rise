import React from 'react';
import { Modal, View, Text, StyleSheet, ActivityIndicator } from 'react-native';
import { PressableScale } from '../../../components/ui';
import { colors, radii, spacing, typography } from '../../../theme';
import type { Challenge } from '../types';

interface Props {
  challenge: Challenge | null;
  isSubmitting: boolean;
  errorMessage?: string | null;
  onConfirm: () => void;
  onCancel: () => void;
}

export function JoinConfirmationSheet({
  challenge,
  isSubmitting,
  errorMessage,
  onConfirm,
  onCancel,
}: Props) {
  if (!challenge) return null;

  return (
    <Modal visible={!!challenge} animationType="slide" transparent onRequestClose={onCancel}>
      <View style={styles.backdrop}>
        <View style={styles.sheet}>
          <View style={styles.handle} />

          <Text style={styles.title}>Join &quot;{challenge.name}&quot;?</Text>

          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Target</Text>
            <Text style={styles.detailValue}>
              {challenge.targetAmount ? `GHS ${(challenge.targetAmount / 100).toFixed(2)}` : '—'}
            </Text>
          </View>
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Duration</Text>
            <Text style={styles.detailValue}>{challenge.targetDurationDays} days</Text>
          </View>

          {errorMessage && (
            <Text style={styles.errorText} testID="join-error">
              {errorMessage}
            </Text>
          )}

          <PressableScale
            onPress={onConfirm}
            disabled={isSubmitting}
            style={[styles.confirmButton, isSubmitting && styles.disabledButton]}
            accessibilityRole="button"
            accessibilityLabel="Confirm join challenge"
          >
            {isSubmitting ? (
              <ActivityIndicator color={colors.neutral[900]} testID="join-submitting-spinner" />
            ) : (
              <Text style={styles.confirmLabel}>Confirm</Text>
            )}
          </PressableScale>

          <PressableScale onPress={onCancel} disabled={isSubmitting} style={styles.cancelButton}>
            <Text style={styles.cancelLabel}>Cancel</Text>
          </PressableScale>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radii.xl,
    borderTopRightRadius: radii.xl,
    padding: spacing.xl,
  },
  handle: {
    width: 40,
    height: 4,
    backgroundColor: colors.neutral[200],
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: spacing.lg,
  },
  title: { ...typography.h3, color: colors.textPrimary, marginBottom: spacing.lg },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: spacing.xs },
  detailLabel: { fontSize: 14, color: colors.textSecondary },
  detailValue: { fontSize: 14, fontWeight: '700', color: colors.textPrimary },
  errorText: { fontSize: 13, color: colors.status.error, marginTop: spacing.sm },
  confirmButton: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.sm,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginTop: spacing.xl,
  },
  disabledButton: { opacity: 0.6 },
  confirmLabel: { fontSize: 15, fontWeight: '700', color: colors.neutral[900] },
  cancelButton: { paddingVertical: spacing.sm, alignItems: 'center', marginTop: spacing.xxs },
  cancelLabel: { fontSize: 14, color: colors.textSecondary },
});
