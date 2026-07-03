import React, { useCallback, useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
  ScrollView,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchDowngradePreview,
  commitDowngrade,
  DowngradePreviewResponse,
} from '../../api/subscriptionApi';
import { RootStackParamList } from '../../navigation/RootNavigator';

type Nav = NativeStackNavigationProp<RootStackParamList, 'SubscriptionDowngrade'>;

type Step = 'preview' | 'confirm' | 'confirming' | 'success' | 'error';

const DARK = '#1A1A2E';
const MUTED = '#6B7280';
const INDIGO = '#4F46E5';
const BACKGROUND = '#F8F9FF';
const BLUE = '#1E40AF';
const RED = '#DC2626';

export function DowngradeScreen() {
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();

  const {
    data: preview,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['subscription', 'downgrade-preview'],
    queryFn: fetchDowngradePreview,
  });

  const [step, setStep] = useState<Step>('preview');
  const [acknowledged, setAcknowledged] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleCancel = useCallback(() => {
    navigation.goBack();
  }, [navigation]);

  const handleContinue = useCallback(() => {
    setStep('confirm');
  }, []);

  const handleBackToPreview = useCallback(() => {
    setStep('preview');
    setAcknowledged(false);
  }, []);

  const handleConfirm = useCallback(async () => {
    setStep('confirming');
    setErrorMessage(null);
    try {
      await commitDowngrade();
      await queryClient.invalidateQueries({ queryKey: ['subscription', 'status'] });
      await queryClient.invalidateQueries({ queryKey: ['vaults'] });
      await queryClient.invalidateQueries({ queryKey: ['susu'] });
      setStep('success');
    } catch {
      setErrorMessage('Something went wrong completing your downgrade. Please try again.');
      setStep('error');
    }
  }, [queryClient]);

  if (isLoading) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <ActivityIndicator size="large" color={INDIGO} testID="downgrade-loading" />
        </View>
      </SafeAreaView>
    );
  }

  if (isError || !preview) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Text style={styles.title}>Couldn&apos;t load your downgrade preview</Text>
          <Text style={styles.subtitle}>Please try again.</Text>
          <TouchableOpacity
            style={styles.secondaryButton}
            onPress={handleCancel}
            accessibilityRole="button"
            accessibilityLabel="Go back"
          >
            <Text style={styles.secondaryButtonLabel}>Back</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  if (step === 'success') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Text style={styles.title} testID="downgrade-success">
            You&apos;re now on the Free plan
          </Text>
          <Text style={styles.subtitle}>
            Any frozen resources remain visible — you can re-upgrade any time to unfreeze them.
          </Text>
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={handleCancel}
            accessibilityRole="button"
            accessibilityLabel="Done"
          >
            <Text style={styles.primaryButtonLabel}>Done</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  if (step === 'error') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Text style={styles.title} testID="downgrade-error">
            Downgrade didn&apos;t complete
          </Text>
          <Text style={styles.subtitle}>{errorMessage}</Text>
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={handleBackToPreview}
            accessibilityRole="button"
            accessibilityLabel="Retry downgrade"
          >
            <Text style={styles.primaryButtonLabel}>Try Again</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.secondaryButton}
            onPress={handleCancel}
            accessibilityRole="button"
            accessibilityLabel="Go back"
          >
            <Text style={styles.secondaryButtonLabel}>Back</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  if (step === 'confirm' || step === 'confirming') {
    return (
      <SafeAreaView style={styles.safe}>
        <ScrollView contentContainerStyle={styles.content}>
          <Text style={styles.title}>Confirm Downgrade</Text>

          {hasImpact(preview) && (
            <Text style={styles.subtitle}>
              {preview.vaults_to_be_frozen.length + preview.susu_groups_to_be_frozen.length}{' '}
              resource
              {preview.vaults_to_be_frozen.length + preview.susu_groups_to_be_frozen.length > 1
                ? 's'
                : ''}{' '}
              listed on the previous screen will be frozen immediately.
            </Text>
          )}

          <TouchableOpacity
            style={styles.checkboxRow}
            onPress={() => setAcknowledged(!acknowledged)}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: acknowledged }}
            accessibilityLabel="I understand my resources listed above will be frozen"
          >
            <View
              style={[styles.checkbox, acknowledged && styles.checkboxChecked]}
              testID="downgrade-ack-checkbox"
            >
              {acknowledged && <Text style={styles.checkmark}>✓</Text>}
            </View>
            <Text style={styles.checkboxLabel}>
              I understand my resources listed above will be frozen.
            </Text>
          </TouchableOpacity>

          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={styles.cancelButton}
              onPress={handleCancel}
              disabled={step === 'confirming'}
              accessibilityRole="button"
              accessibilityLabel="Cancel downgrade"
            >
              <Text style={styles.cancelLabel}>Cancel</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[
                styles.confirmButton,
                (!acknowledged || step === 'confirming') && styles.disabledButton,
              ]}
              disabled={!acknowledged || step === 'confirming'}
              onPress={handleConfirm}
              accessibilityRole="button"
              accessibilityLabel="Confirm downgrade"
              accessibilityState={{ disabled: !acknowledged || step === 'confirming' }}
            >
              {step === 'confirming' ? (
                <ActivityIndicator color="#FFFFFF" testID="downgrade-confirming-spinner" />
              ) : (
                <Text style={styles.confirmLabel}>Confirm Downgrade</Text>
              )}
            </TouchableOpacity>
          </View>

          <TouchableOpacity
            onPress={handleBackToPreview}
            disabled={step === 'confirming'}
            style={styles.backLink}
            accessibilityRole="button"
            accessibilityLabel="Back to preview"
          >
            <Text style={styles.backLinkLabel}>Back to preview</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // step === 'preview'
  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>Downgrade to Free</Text>

        {hasImpact(preview) ? (
          <>
            {preview.vaults_to_be_frozen.length > 0 && (
              <>
                <Text style={styles.sectionLabel}>These vaults will be frozen:</Text>
                {preview.vaults_to_be_frozen.map(vault => (
                  <View
                    key={vault.vault_id}
                    style={styles.resourceRow}
                    testID={`frozen-vault-${vault.vault_id}`}
                  >
                    <Text style={styles.resourceName}>{vault.vault_name}</Text>
                    <Text style={styles.resourceType}>{vault.vault_type}</Text>
                  </View>
                ))}
                <Text style={styles.warningCopy} testID="frozen-funds-warning">
                  Your locked vault funds remain safe but will not be accessible to deposit,
                  withdraw, or unlock until you re-upgrade or resolve the limit.
                </Text>
              </>
            )}

            {preview.susu_groups_to_be_frozen.length > 0 && (
              <>
                <Text style={styles.sectionLabel}>These susu groups will be frozen:</Text>
                {preview.susu_groups_to_be_frozen.map(group => (
                  <View
                    key={group.susu_group_id}
                    style={styles.resourceRow}
                    testID={`frozen-susu-${group.susu_group_id}`}
                  >
                    <Text style={styles.resourceName}>{group.susu_group_name}</Text>
                  </View>
                ))}
              </>
            )}
          </>
        ) : (
          <Text style={styles.noImpactText} testID="no-impact-message">
            You&apos;re within the Free plan&apos;s limits — no resources will be frozen.
          </Text>
        )}

        <View style={styles.buttonRow}>
          <TouchableOpacity
            style={styles.cancelButton}
            onPress={handleCancel}
            accessibilityRole="button"
            accessibilityLabel="Cancel downgrade"
          >
            <Text style={styles.cancelLabel}>Cancel</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.continueButton}
            onPress={handleContinue}
            accessibilityRole="button"
            accessibilityLabel="Continue to confirmation"
          >
            <Text style={styles.continueLabel}>Continue</Text>
          </TouchableOpacity>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function hasImpact(preview: DowngradePreviewResponse): boolean {
  return preview.vaults_to_be_frozen.length > 0 || preview.susu_groups_to_be_frozen.length > 0;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: BACKGROUND },
  content: { padding: 20 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  title: { fontSize: 20, fontWeight: '800', color: DARK, marginBottom: 16 },
  subtitle: { fontSize: 14, color: MUTED, lineHeight: 21, marginBottom: 16, textAlign: 'center' },
  sectionLabel: { fontSize: 14, fontWeight: '700', color: DARK, marginBottom: 8, marginTop: 8 },
  resourceRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E5E7EB',
  },
  resourceName: { fontSize: 14, color: DARK },
  resourceType: { fontSize: 12, color: MUTED },
  warningCopy: { fontSize: 13, color: '#92400E', marginTop: 16, marginBottom: 8, lineHeight: 19 },
  noImpactText: { fontSize: 14, color: MUTED, marginBottom: 24 },
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
  checkboxChecked: { backgroundColor: INDIGO, borderColor: INDIGO },
  checkmark: { color: '#FFFFFF', fontSize: 14, fontWeight: '700' },
  checkboxLabel: { fontSize: 14, color: DARK, flex: 1, lineHeight: 20 },
  buttonRow: { flexDirection: 'row', gap: 12, marginTop: 12 },
  cancelButton: {
    flex: 1,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: '#D1D5DB',
  },
  continueButton: {
    flex: 1,
    backgroundColor: INDIGO,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  confirmButton: {
    flex: 1,
    backgroundColor: RED,
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  disabledButton: { opacity: 0.4 },
  cancelLabel: { fontSize: 15, fontWeight: '700', color: DARK },
  continueLabel: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
  confirmLabel: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
  backLink: { alignItems: 'center', marginTop: 20 },
  backLinkLabel: { fontSize: 14, color: INDIGO },
  primaryButton: {
    backgroundColor: INDIGO,
    borderRadius: 14,
    paddingVertical: 15,
    paddingHorizontal: 24,
    alignSelf: 'stretch',
    alignItems: 'center',
    marginTop: 8,
  },
  primaryButtonLabel: { fontSize: 16, fontWeight: '700', color: '#FFFFFF' },
  secondaryButton: { paddingVertical: 12, marginTop: 8 },
  secondaryButtonLabel: { fontSize: 14, color: BLUE },
});
