import React, { useCallback, useState } from 'react';
import {
  View,
  Text,
  ActivityIndicator,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Animated from 'react-native-reanimated';
import {
  fetchDowngradePreview,
  commitDowngrade,
  DowngradePreviewResponse,
} from '../../api/subscriptionApi';
import { RootStackParamList } from '../../navigation/RootNavigator';
import {
  Banner,
  GradientHero,
  Icon,
  PressableScale,
  ScreenHeader,
  fadeInUp,
} from '../../components/ui';
import { PrimaryButton } from '../../components/PrimaryButton';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'SubscriptionDowngrade'>;

type Step = 'preview' | 'confirm' | 'confirming' | 'success' | 'error';

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
          <ActivityIndicator size="large" color={colors.gold.base} testID="downgrade-loading" />
        </View>
      </SafeAreaView>
    );
  }

  if (isError || !preview) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Animated.View entering={fadeInUp(40)} style={styles.centeredInner}>
            <View style={styles.failureIconBadge}>
              <Icon name="close-circle" size={40} color={colors.status.error} />
            </View>
            <Text style={styles.centeredTitle}>Couldn&apos;t load your downgrade preview</Text>
            <Text style={styles.centeredSubtitle}>Please try again.</Text>
            <PressableScale
              style={styles.secondaryButton}
              onPress={handleCancel}
              accessibilityRole="button"
              accessibilityLabel="Go back"
            >
              <Text style={styles.secondaryButtonLabel}>Back</Text>
            </PressableScale>
          </Animated.View>
        </View>
      </SafeAreaView>
    );
  }

  if (step === 'success') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Animated.View entering={fadeInUp(40)} style={styles.centeredInner}>
            <View style={styles.successIconBadge}>
              <Icon name="checkmark-circle" size={40} color={colors.status.success} />
            </View>
            <Text style={styles.centeredTitle} testID="downgrade-success">
              You&apos;re now on the Free plan
            </Text>
            <Text style={styles.centeredSubtitle}>
              Any frozen resources remain visible — you can re-upgrade any time to unfreeze them.
            </Text>
            <PrimaryButton
              title="Done"
              onPress={handleCancel}
              accessibilityLabel="Done"
              style={styles.centeredCta}
            />
          </Animated.View>
        </View>
      </SafeAreaView>
    );
  }

  if (step === 'error') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Animated.View entering={fadeInUp(40)} style={styles.centeredInner}>
            <View style={styles.failureIconBadge}>
              <Icon name="close-circle" size={40} color={colors.status.error} />
            </View>
            <Text style={styles.centeredTitle} testID="downgrade-error">
              Downgrade didn&apos;t complete
            </Text>
            <Text style={styles.centeredSubtitle}>{errorMessage}</Text>
            <PrimaryButton
              title="Try Again"
              onPress={handleBackToPreview}
              accessibilityLabel="Retry downgrade"
              style={styles.centeredCta}
            />
            <PressableScale
              style={styles.secondaryButton}
              onPress={handleCancel}
              accessibilityRole="button"
              accessibilityLabel="Go back"
            >
              <Text style={styles.secondaryButtonLabel}>Back</Text>
            </PressableScale>
          </Animated.View>
        </View>
      </SafeAreaView>
    );
  }

  if (step === 'confirm' || step === 'confirming') {
    return (
      <SafeAreaView style={styles.safe}>
        <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
          <ScreenHeader onBack={handleBackToPreview} />

          <Animated.View entering={fadeInUp(40)}>
            <GradientHero icon="arrow-down-circle-outline" title="Confirm Downgrade" />
          </Animated.View>

          <Animated.View entering={fadeInUp(110)} style={styles.card}>
            {hasImpact(preview) && (
              <Text style={styles.cardSubtitle}>
                {preview.vaults_to_be_frozen.length + preview.susu_groups_to_be_frozen.length}{' '}
                resource
                {preview.vaults_to_be_frozen.length + preview.susu_groups_to_be_frozen.length > 1
                  ? 's'
                  : ''}{' '}
                listed on the previous screen will be frozen immediately.
              </Text>
            )}

            <PressableScale
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
                {acknowledged && <Icon name="checkmark" size={14} color={colors.neutral[900]} />}
              </View>
              <Text style={styles.checkboxLabel}>
                I understand my resources listed above will be frozen.
              </Text>
            </PressableScale>

            <View style={styles.buttonRow}>
              <TouchableOpacity
                style={styles.cancelButton}
                onPress={handleCancel}
                disabled={step === 'confirming'}
                activeOpacity={0.8}
                accessibilityRole="button"
                accessibilityLabel="Cancel downgrade"
              >
                <Text style={styles.cancelLabel}>Cancel</Text>
              </TouchableOpacity>
              <PressableScale
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
                  <ActivityIndicator
                    color={colors.neutral[0]}
                    testID="downgrade-confirming-spinner"
                  />
                ) : (
                  <Text style={styles.confirmLabel}>Confirm Downgrade</Text>
                )}
              </PressableScale>
            </View>

            <PressableScale
              onPress={handleBackToPreview}
              disabled={step === 'confirming'}
              style={styles.backLink}
              accessibilityRole="button"
              accessibilityLabel="Back to preview"
            >
              <Text style={styles.backLinkLabel}>Back to preview</Text>
            </PressableScale>
          </Animated.View>
        </ScrollView>
      </SafeAreaView>
    );
  }

  // step === 'preview'
  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <ScreenHeader onBack={handleCancel} />

        <Animated.View entering={fadeInUp(40)}>
          <GradientHero icon="arrow-down-circle-outline" title="Downgrade to Free" />
        </Animated.View>

        <Animated.View entering={fadeInUp(110)} style={styles.card}>
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
                  <Banner
                    tone="warning"
                    message="Your locked vault funds remain safe but will not be accessible to deposit, withdraw, or unlock until you re-upgrade or resolve the limit."
                    testID="frozen-funds-warning"
                  />
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
        </Animated.View>

        <Animated.View entering={fadeInUp(180)} style={styles.buttonRow}>
          <TouchableOpacity
            style={styles.cancelButton}
            onPress={handleCancel}
            activeOpacity={0.8}
            accessibilityRole="button"
            accessibilityLabel="Cancel downgrade"
          >
            <Text style={styles.cancelLabel}>Cancel</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.continueButton}
            onPress={handleContinue}
            activeOpacity={0.8}
            accessibilityRole="button"
            accessibilityLabel="Continue to confirmation"
          >
            <Text style={styles.continueLabel}>Continue</Text>
          </TouchableOpacity>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

function hasImpact(preview: DowngradePreviewResponse): boolean {
  return preview.vaults_to_be_frozen.length > 0 || preview.susu_groups_to_be_frozen.length > 0;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  scroll: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing['3xl'] },
  centeredInner: { alignItems: 'center', alignSelf: 'stretch' },
  centeredTitle: {
    ...typography.h2,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textAlign: 'center',
  },
  centeredSubtitle: {
    fontSize: 14,
    color: colors.textSecondary,
    lineHeight: 21,
    marginBottom: spacing['2xl'],
    textAlign: 'center',
  },
  centeredCta: { alignSelf: 'stretch', marginTop: spacing.xs },
  successIconBadge: {
    width: 76,
    height: 76,
    borderRadius: radii.pill,
    backgroundColor: colors.status.successBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
  failureIconBadge: {
    width: 76,
    height: 76,
    borderRadius: radii.pill,
    backgroundColor: colors.status.errorBg,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    marginBottom: spacing.xl,
    ...shadows.sm,
  },
  cardSubtitle: {
    fontSize: 14,
    color: colors.textSecondary,
    lineHeight: 21,
    marginBottom: spacing.lg,
  },
  sectionLabel: {
    fontSize: 14,
    fontWeight: '700',
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    marginTop: spacing.sm,
  },
  resourceRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  resourceName: { fontSize: 14, color: colors.textPrimary },
  resourceType: { fontSize: 12, color: colors.textSecondary },
  noImpactText: { fontSize: 14, color: colors.textSecondary },
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
  checkboxChecked: { backgroundColor: colors.gold.base, borderColor: colors.gold.base },
  checkboxLabel: { fontSize: 14, color: colors.textPrimary, flex: 1, lineHeight: 20 },
  buttonRow: { flexDirection: 'row', gap: spacing.md, marginTop: spacing.md },
  cancelButton: {
    flex: 1,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: colors.borderStrong,
  },
  continueButton: {
    flex: 1,
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  confirmButton: {
    flex: 1,
    backgroundColor: colors.status.error,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabledButton: { opacity: 0.4 },
  cancelLabel: { fontSize: 15, fontWeight: '700', color: colors.textPrimary },
  continueLabel: { fontSize: 15, fontWeight: '700', color: colors.neutral[900] },
  confirmLabel: { fontSize: 15, fontWeight: '700', color: colors.neutral[0] },
  backLink: { alignItems: 'center', marginTop: spacing.xl },
  backLinkLabel: { fontSize: 14, color: colors.gold.text },
  secondaryButton: { paddingVertical: spacing.md, marginTop: spacing.sm },
  secondaryButtonLabel: { fontSize: 14, color: colors.textSecondary },
});
