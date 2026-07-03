import React from 'react';
import { Modal, View, Text, Pressable, StyleSheet, ActivityIndicator } from 'react-native';
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

          <Pressable
            onPress={onConfirm}
            disabled={isSubmitting}
            style={[styles.confirmButton, isSubmitting && styles.disabledButton]}
            accessibilityRole="button"
            accessibilityLabel="Confirm join challenge"
          >
            {isSubmitting ? (
              <ActivityIndicator color="#FFFFFF" testID="join-submitting-spinner" />
            ) : (
              <Text style={styles.confirmLabel}>Confirm</Text>
            )}
          </Pressable>

          <Pressable onPress={onCancel} disabled={isSubmitting} style={styles.cancelButton}>
            <Text style={styles.cancelLabel}>Cancel</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 20,
  },
  handle: {
    width: 40,
    height: 4,
    backgroundColor: '#E5E7EB',
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: 16,
  },
  title: { fontSize: 18, fontWeight: '700', color: '#111827', marginBottom: 16 },
  detailRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 },
  detailLabel: { fontSize: 14, color: '#6B7280' },
  detailValue: { fontSize: 14, fontWeight: '700', color: '#111827' },
  errorText: { fontSize: 13, color: '#DC2626', marginTop: 10 },
  confirmButton: {
    backgroundColor: '#1A1A1A',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 20,
  },
  disabledButton: { opacity: 0.6 },
  confirmLabel: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
  cancelButton: { paddingVertical: 10, alignItems: 'center', marginTop: 4 },
  cancelLabel: { fontSize: 14, color: '#6B7280' },
});
