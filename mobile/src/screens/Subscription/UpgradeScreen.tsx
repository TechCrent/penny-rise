import React, { useCallback, useState } from 'react';
import { View, Text, ActivityIndicator, StyleSheet, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as WebBrowser from 'expo-web-browser';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import Animated from 'react-native-reanimated';
import { extractApiError } from '../../api/client';
import { initiateUpgrade, confirmUpgrade } from '../../api/subscriptionApi';
import { useSubscriptionStatus } from '../../api/hooks/useSubscriptionStatus';
import { RootStackParamList } from '../../navigation/RootNavigator';
import { GradientHero, Icon, PressableScale, ScreenHeader, fadeInUp } from '../../components/ui';
import { PrimaryButton } from '../../components/PrimaryButton';
import { colors, radii, shadows, spacing, typography } from '../../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'SubscriptionUpgrade'>;

type Phase =
  | 'benefits'
  | 'initiating'
  | 'authorising'
  | 'confirming'
  | 'success'
  | 'paystack_failure'
  | 'network_error';

const PREMIUM_BENEFITS = [
  'Unlimited standard and locked vaults',
  'Organise up to 3 susu groups',
  '20 free peer transfers a month',
  'No fee on transfers over your plan quota',
];

export function UpgradeScreen() {
  const navigation = useNavigation<Nav>();
  const queryClient = useQueryClient();
  const { data: status, isLoading: isStatusLoading } = useSubscriptionStatus();

  const [phase, setPhase] = useState<Phase | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const startUpgrade = useCallback(async () => {
    setErrorMessage(null);
    setPhase('initiating');

    let initiateResponse;
    try {
      initiateResponse = await initiateUpgrade();
    } catch (err: unknown) {
      const apiError = extractApiError(err);
      setPhase('network_error');
      setErrorMessage(apiError?.message ?? "Couldn't start the upgrade. Please try again.");
      return;
    }

    setPhase('authorising');
    try {
      // openBrowserAsync (unlike openAuthSessionAsync) has no way to detect
      // a successful redirect — it only reports how the browser was
      // dismissed, and closing normally after a genuinely successful
      // payment ALSO reports type:'dismiss'. Matching DepositScreen.tsx's
      // established pattern: treat the browser closing as "the user is
      // done looking at it", then ask the server what actually happened —
      // don't try to infer success/failure from the browser result itself.
      await WebBrowser.openBrowserAsync(initiateResponse.authorization_url, {
        toolbarColor: colors.neutral[900],
        showTitle: false,
        enableBarCollapsing: false,
      });
    } catch {
      setPhase('paystack_failure');
      setErrorMessage("Something went wrong opening the payment page. Let's try again.");
      return;
    }

    setPhase('confirming');
    try {
      await confirmUpgrade(initiateResponse.reference);
      await queryClient.invalidateQueries({ queryKey: ['subscription', 'status'] });
      setPhase('success');
    } catch (err: unknown) {
      const apiError = extractApiError(err);
      const httpStatus = axios.isAxiosError(err) ? err.response?.status : undefined;

      if (httpStatus && httpStatus >= 500) {
        setPhase('network_error');
        setErrorMessage("Couldn't reach Stash to confirm your upgrade. Please try again.");
      } else {
        setPhase('paystack_failure');
        setErrorMessage(
          apiError?.message ?? "Something went wrong confirming your upgrade. Let's try again.",
        );
      }
    }
  }, [queryClient]);

  const retry = useCallback(() => {
    setPhase(null);
    setErrorMessage(null);
  }, []);

  if (isStatusLoading && phase === null) {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <ActivityIndicator size="large" color={colors.gold.base} testID="upgrade-loading" />
        </View>
      </SafeAreaView>
    );
  }

  if (phase === null && status?.tier === 'PREMIUM') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Animated.View entering={fadeInUp(40)} style={styles.centeredInner}>
            <View style={styles.goldIconBadge}>
              <Icon name="star" size={38} color={colors.gold.text} />
            </View>
            <Text style={styles.centeredTitle} testID="already-premium-message">
              You&apos;re already on Premium
            </Text>
            <Text style={styles.centeredSubtitle}>
              Enjoy unlimited vaults, susu groups, and transfers.
            </Text>
          </Animated.View>
        </View>
      </SafeAreaView>
    );
  }

  if (phase === 'initiating' || phase === 'authorising' || phase === 'confirming') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <ActivityIndicator size="large" color={colors.gold.base} testID="upgrade-progress" />
          <Text style={styles.progressText}>
            {phase === 'authorising' ? 'Opening payment…' : 'Confirming your upgrade…'}
          </Text>
        </View>
      </SafeAreaView>
    );
  }

  if (phase === 'success') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Animated.View entering={fadeInUp(40)} style={styles.centeredInner}>
            <View style={styles.successIconBadge}>
              <Icon name="checkmark-circle" size={40} color={colors.status.success} />
            </View>
            <Text style={styles.centeredTitle} testID="upgrade-success">
              You&apos;re now on Premium!
            </Text>
            <Text style={styles.centeredSubtitle}>All limits are lifted immediately.</Text>
            <PrimaryButton
              title="Done"
              onPress={() => navigation.goBack()}
              style={styles.centeredCta}
            />
          </Animated.View>
        </View>
      </SafeAreaView>
    );
  }

  if (phase === 'paystack_failure' || phase === 'network_error') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Animated.View entering={fadeInUp(40)} style={styles.centeredInner}>
            <View style={styles.failureIconBadge}>
              <Icon name="close-circle" size={40} color={colors.status.error} />
            </View>
            <Text style={styles.centeredTitle} testID="upgrade-error-title">
              {phase === 'network_error' ? "Couldn't reach Stash" : "Upgrade didn't complete"}
            </Text>
            <Text style={styles.centeredSubtitle} testID="upgrade-error-message">
              {errorMessage ?? 'Please try again.'}
            </Text>
            <PrimaryButton
              title="Try Again"
              onPress={retry}
              accessibilityLabel="Retry upgrade"
              style={styles.centeredCta}
            />
            <PressableScale
              style={styles.secondaryButton}
              onPress={() => navigation.goBack()}
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

  // benefits state — the default entry point
  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <ScreenHeader onBack={() => navigation.goBack()} />

        <Animated.View entering={fadeInUp(40)}>
          <GradientHero icon="star" title="Stash Premium" />
        </Animated.View>

        <Animated.View entering={fadeInUp(110)} style={styles.card}>
          <View style={styles.priceRow}>
            <Text style={styles.price}>GHS 15 / month</Text>
          </View>

          <View style={styles.divider} />

          <View style={styles.benefitsList}>
            {PREMIUM_BENEFITS.map((benefit, i) => (
              <View key={i} style={styles.benefitRow}>
                <Icon
                  name="checkmark-circle"
                  size={18}
                  color={colors.status.success}
                  style={styles.benefitIcon}
                />
                <Text style={styles.benefitText}>{benefit}</Text>
              </View>
            ))}
          </View>
        </Animated.View>

        <Animated.View entering={fadeInUp(180)}>
          <PrimaryButton
            title="Upgrade to Premium"
            onPress={startUpgrade}
            style={styles.cta}
          />
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
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
    textAlign: 'center',
    marginBottom: spacing['2xl'],
    lineHeight: 21,
  },
  centeredCta: { alignSelf: 'stretch', marginTop: spacing.xs },
  progressText: {
    fontSize: 14,
    color: colors.textSecondary,
    textAlign: 'center',
    marginTop: spacing.lg,
    lineHeight: 21,
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
  priceRow: { alignItems: 'center' },
  price: { ...typography.numericLarge, color: colors.gold.text },
  divider: {
    height: StyleSheet.hairlineWidth,
    backgroundColor: colors.border,
    marginVertical: spacing.xl,
  },
  benefitsList: { alignSelf: 'stretch' },
  benefitRow: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: spacing.lg },
  benefitIcon: { marginRight: spacing.sm, marginTop: 1 },
  benefitText: { fontSize: 15, color: colors.textPrimary, flex: 1, lineHeight: 21 },
  cta: { marginTop: spacing.xxs },
  secondaryButton: { paddingVertical: spacing.md, marginTop: spacing.sm },
  secondaryButtonLabel: { fontSize: 14, color: colors.textSecondary },
  goldIconBadge: {
    width: 76,
    height: 76,
    borderRadius: radii.pill,
    backgroundColor: colors.gold.light,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
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
});
