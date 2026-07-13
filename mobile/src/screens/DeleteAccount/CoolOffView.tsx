import React from 'react';
import { View, Text, ActivityIndicator, StyleSheet } from 'react-native';
import { PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';
import type { DeletionRequestStatus } from './types';

interface Props {
  request: DeletionRequestStatus;
  isCancelling: boolean;
  cancelError: unknown;
  onCancel: () => void;
}

export function CoolOffView({ request, isCancelling, cancelError, onCancel }: Props) {
  const scheduledDate = new Date(request.scheduled_completion_at).toLocaleDateString('en-GH', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  });

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Your account is scheduled for deletion</Text>

      <Text style={styles.paragraph}>
        Your account will be permanently deleted on <Text style={styles.date}>{scheduledDate}</Text>
        , unless you cancel before then.
      </Text>

      <Text style={styles.paragraph}>
        Until that date, your account works normally — you can keep using Stash as usual.
      </Text>

      {cancelError != null && (
        <Text style={styles.errorText} testID="cancel-error">
          Something went wrong cancelling your request. Please try again.
        </Text>
      )}

      <PressableScale
        style={[styles.cancelButton, isCancelling && styles.disabledButton]}
        disabled={isCancelling}
        onPress={onCancel}
        accessibilityRole="button"
        accessibilityLabel="Cancel deletion request"
      >
        {isCancelling ? (
          <ActivityIndicator color={colors.neutral[0]} testID="cancel-spinner" />
        ) : (
          <Text style={styles.cancelLabel}>Cancel Request</Text>
        )}
      </PressableScale>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background, padding: spacing.xl },
  title: { ...typography.h3, fontSize: 20, color: colors.textPrimary, marginBottom: spacing.lg },
  paragraph: { fontSize: 14, color: colors.textPrimary, marginBottom: spacing.md, lineHeight: 21 },
  date: { fontWeight: '700' },
  errorText: { fontSize: 14, color: colors.status.error, marginBottom: spacing.sm },
  cancelButton: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.sm,
    paddingVertical: spacing.md,
    alignItems: 'center',
    marginTop: spacing.xl,
  },
  disabledButton: { opacity: 0.6 },
  cancelLabel: { fontSize: 15, fontWeight: '700', color: colors.neutral[900] },
});
