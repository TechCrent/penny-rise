import React from 'react';
import { View, Text, Pressable, ActivityIndicator, StyleSheet } from 'react-native';
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

      <Pressable
        style={[styles.cancelButton, isCancelling && styles.disabledButton]}
        disabled={isCancelling}
        onPress={onCancel}
        accessibilityRole="button"
        accessibilityLabel="Cancel deletion request"
      >
        {isCancelling ? (
          <ActivityIndicator color="#FFFFFF" testID="cancel-spinner" />
        ) : (
          <Text style={styles.cancelLabel}>Cancel Request</Text>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F9FAFB', padding: 20 },
  title: { fontSize: 20, fontWeight: '800', color: '#111827', marginBottom: 16 },
  paragraph: { fontSize: 14, color: '#111827', marginBottom: 14, lineHeight: 21 },
  date: { fontWeight: '700' },
  errorText: { fontSize: 14, color: '#DC2626', marginBottom: 10 },
  cancelButton: {
    backgroundColor: '#1A1A1A',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 20,
  },
  disabledButton: { opacity: 0.6 },
  cancelLabel: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
});
