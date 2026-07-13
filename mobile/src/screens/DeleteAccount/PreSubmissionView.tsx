import React, { useState } from 'react';
import { ScrollView, View, Text, ActivityIndicator, StyleSheet } from 'react-native';
import { Icon, PressableScale } from '../../components/ui';
import { colors, radii, spacing, typography } from '../../theme';
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
        <ActivityIndicator style={styles.blockersLoading} color={colors.gold.base} testID="blockers-loading" />
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

      <PressableScale
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
          {acknowledged && <Icon name="checkmark" size={14} color={colors.neutral[0]} />}
        </View>
        <Text style={styles.checkboxLabel}>
          I understand this will begin a 30-day account deletion process.
        </Text>
      </PressableScale>

      {submitError != null && (
        <Text style={styles.errorText} testID="submit-error">
          Something went wrong submitting your request. Please try again.
        </Text>
      )}

      <PressableScale
        style={[styles.confirmButton, (!acknowledged || isSubmitting) && styles.disabledButton]}
        disabled={!acknowledged || isSubmitting}
        onPress={onSubmit}
        accessibilityRole="button"
        accessibilityLabel="Confirm delete account"
      >
        {isSubmitting ? (
          <ActivityIndicator color={colors.neutral[0]} testID="submit-spinner" />
        ) : (
          <Text style={styles.confirmLabel}>Delete My Account</Text>
        )}
      </PressableScale>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  container: { padding: spacing.xl, paddingBottom: spacing['4xl'] },
  title: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.lg },
  paragraph: { fontSize: 14, color: colors.textPrimary, marginBottom: spacing.md, lineHeight: 21 },
  blockersLoading: { marginVertical: spacing.lg },
  blockersBox: {
    backgroundColor: colors.status.warningBg,
    borderRadius: radii.sm,
    padding: spacing.md,
    marginVertical: spacing.md,
  },
  blockersTitle: { fontSize: 14, fontWeight: '700', color: colors.textPrimary, marginBottom: spacing.xs },
  blockersSubtitle: { fontSize: 12, color: colors.textSecondary, marginBottom: spacing.sm },
  blockerItem: { fontSize: 14, color: colors.textPrimary, marginBottom: spacing.xs },
  checkboxRow: { flexDirection: 'row', alignItems: 'flex-start', marginVertical: spacing.xl },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 5,
    borderWidth: 2,
    borderColor: colors.borderStrong,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.sm,
    marginTop: 2,
  },
  checkboxChecked: { backgroundColor: colors.status.error, borderColor: colors.status.error },
  checkboxLabel: { fontSize: 14, color: colors.textPrimary, flex: 1 },
  errorText: { fontSize: 14, color: colors.status.error, marginBottom: spacing.sm },
  confirmButton: {
    backgroundColor: colors.status.error,
    borderRadius: radii.sm,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabledButton: { opacity: 0.4 },
  confirmLabel: { fontSize: 15, fontWeight: '700', color: colors.neutral[0] },
});
