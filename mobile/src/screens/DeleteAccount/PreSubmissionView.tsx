import React, { useState } from 'react';
import { ScrollView, View, Text, Pressable, ActivityIndicator, StyleSheet } from 'react-native';
import type { DeletionBlocker } from './types';

interface Props {
  blockers: DeletionBlocker[];
  isLoadingBlockers: boolean;
  isSubmitting: boolean;
  submitError: unknown;
  onSubmit: () => void;
}

export function PreSubmissionView({
  blockers,
  isLoadingBlockers,
  isSubmitting,
  submitError,
  onSubmit,
}: Props) {
  const [acknowledged, setAcknowledged] = useState(false);

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.container}>
      <Text style={styles.title}>Delete your account</Text>

      <Text style={styles.paragraph}>
        Deleting your account is not immediate. When you confirm below, we start a 30-day waiting
        period. During those 30 days, your account works normally, and you can cancel the request at
        any time — there&apos;s a Cancel Request button right on this screen the whole time.
      </Text>

      <Text style={styles.paragraph}>
        After 30 days, if there&apos;s nothing still in progress that needs to finish first (see
        below), your account is closed: your profile data is removed, all your sessions are signed
        out, and your vault and wallet accounts are closed.
      </Text>

      <Text style={styles.paragraph}>
        Your transaction history is kept for 7 years, without your name attached, as required for
        financial record-keeping.
      </Text>

      {isLoadingBlockers ? (
        <ActivityIndicator style={styles.blockersLoading} testID="blockers-loading" />
      ) : blockers.length > 0 ? (
        <View style={styles.blockersBox} testID="blockers-list">
          <Text style={styles.blockersTitle}>Some things are still open on your account</Text>
          <Text style={styles.blockersSubtitle}>
            You can still submit your deletion request. These will need to be resolved before the
            30-day period ends, or your account will stay pending deletion until they are:
          </Text>
          {blockers.map((blocker, i) => (
            <Text key={i} style={styles.blockerItem}>
              • {blocker.description}
            </Text>
          ))}
        </View>
      ) : null}

      <Pressable
        style={styles.checkboxRow}
        onPress={() => setAcknowledged(v => !v)}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: acknowledged }}
        accessibilityLabel="I understand this will begin a 30-day account deletion process"
      >
        <View
          style={[styles.checkbox, acknowledged && styles.checkboxChecked]}
          testID="ack-checkbox"
        >
          {acknowledged && <Text style={styles.checkmark}>✓</Text>}
        </View>
        <Text style={styles.checkboxLabel}>
          I understand this will begin a 30-day account deletion process.
        </Text>
      </Pressable>

      {submitError != null && (
        <Text style={styles.errorText} testID="submit-error">
          Something went wrong submitting your request. Please try again.
        </Text>
      )}

      <Pressable
        style={[styles.confirmButton, (!acknowledged || isSubmitting) && styles.disabledButton]}
        disabled={!acknowledged || isSubmitting}
        onPress={onSubmit}
        accessibilityRole="button"
        accessibilityLabel="Confirm delete account"
      >
        {isSubmitting ? (
          <ActivityIndicator color="#FFFFFF" testID="submit-spinner" />
        ) : (
          <Text style={styles.confirmLabel}>Delete My Account</Text>
        )}
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  container: { padding: 20, paddingBottom: 40 },
  title: { fontSize: 22, fontWeight: '800', color: '#111827', marginBottom: 16 },
  paragraph: { fontSize: 14, color: '#111827', marginBottom: 14, lineHeight: 21 },
  blockersLoading: { marginVertical: 16 },
  blockersBox: {
    backgroundColor: '#FEF3C7',
    borderRadius: 8,
    padding: 14,
    marginVertical: 14,
  },
  blockersTitle: { fontSize: 14, fontWeight: '700', color: '#111827', marginBottom: 4 },
  blockersSubtitle: { fontSize: 12, color: '#6B7280', marginBottom: 8 },
  blockerItem: { fontSize: 14, color: '#111827', marginBottom: 4 },
  checkboxRow: { flexDirection: 'row', alignItems: 'flex-start', marginVertical: 20 },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 4,
    borderWidth: 2,
    borderColor: '#D1D5DB',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
    marginTop: 2,
  },
  checkboxChecked: { backgroundColor: '#1A1A1A', borderColor: '#1A1A1A' },
  checkmark: { color: '#FFFFFF', fontSize: 14, fontWeight: '700' },
  checkboxLabel: { fontSize: 14, color: '#111827', flex: 1 },
  errorText: { fontSize: 14, color: '#DC2626', marginBottom: 10 },
  confirmButton: {
    backgroundColor: '#DC2626',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
  },
  disabledButton: { opacity: 0.4 },
  confirmLabel: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
});
