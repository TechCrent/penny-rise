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
import * as WebBrowser from 'expo-web-browser';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { extractApiError } from '../../api/client';
import { initiateUpgrade, confirmUpgrade } from '../../api/subscriptionApi';
import { useSubscriptionStatus } from '../../api/hooks/useSubscriptionStatus';
import { RootStackParamList } from '../../navigation/RootNavigator';

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

const DARK = '#1A1A2E';
const MUTED = '#6B7280';
const INDIGO = '#4F46E5';
const BACKGROUND = '#F8F9FF';
const GREEN = '#059669';

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
        toolbarColor: DARK,
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
          <ActivityIndicator size="large" color={INDIGO} testID="upgrade-loading" />
        </View>
      </SafeAreaView>
    );
  }

  if (phase === null && status?.tier === 'PREMIUM') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Text style={styles.title} testID="already-premium-message">
            You&apos;re already on Premium
          </Text>
          <Text style={styles.subtitle}>Enjoy unlimited vaults, susu groups, and transfers.</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (phase === 'initiating' || phase === 'authorising' || phase === 'confirming') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <ActivityIndicator size="large" color={INDIGO} testID="upgrade-progress" />
          <Text style={styles.subtitle}>
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
          <Text style={styles.successIcon}>✅</Text>
          <Text style={styles.title} testID="upgrade-success">
            You&apos;re now on Premium!
          </Text>
          <Text style={styles.subtitle}>All limits are lifted immediately.</Text>
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={() => navigation.goBack()}
            accessibilityRole="button"
            accessibilityLabel="Done"
          >
            <Text style={styles.primaryButtonLabel}>Done</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  if (phase === 'paystack_failure' || phase === 'network_error') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Text style={styles.failureIcon}>❌</Text>
          <Text style={styles.title} testID="upgrade-error-title">
            {phase === 'network_error' ? "Couldn't reach Stash" : "Upgrade didn't complete"}
          </Text>
          <Text style={styles.subtitle} testID="upgrade-error-message">
            {errorMessage ?? 'Please try again.'}
          </Text>
          <TouchableOpacity
            style={styles.primaryButton}
            onPress={retry}
            accessibilityRole="button"
            accessibilityLabel="Retry upgrade"
          >
            <Text style={styles.primaryButtonLabel}>Try Again</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.secondaryButton}
            onPress={() => navigation.goBack()}
            accessibilityRole="button"
            accessibilityLabel="Go back"
          >
            <Text style={styles.secondaryButtonLabel}>Back</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  // benefits state — the default entry point
  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={styles.title}>Stash Premium</Text>
        <Text style={styles.price}>GHS 15 / month</Text>

        <View style={styles.benefitsList}>
          {PREMIUM_BENEFITS.map((benefit, i) => (
            <View key={i} style={styles.benefitRow}>
              <Text style={styles.benefitBullet}>✓</Text>
              <Text style={styles.benefitText}>{benefit}</Text>
            </View>
          ))}
        </View>

        <TouchableOpacity
          style={styles.primaryButton}
          onPress={startUpgrade}
          activeOpacity={0.85}
          accessibilityRole="button"
          accessibilityLabel="Upgrade to Premium"
        >
          <Text style={styles.primaryButtonLabel}>Upgrade to Premium</Text>
        </TouchableOpacity>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: BACKGROUND },
  content: { padding: 20, alignItems: 'center' },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  title: { fontSize: 22, fontWeight: '800', color: DARK, marginBottom: 8, textAlign: 'center' },
  subtitle: { fontSize: 14, color: MUTED, textAlign: 'center', marginBottom: 24, lineHeight: 21 },
  price: { fontSize: 18, fontWeight: '700', color: INDIGO, marginBottom: 24 },
  benefitsList: { alignSelf: 'stretch', marginBottom: 32 },
  benefitRow: { flexDirection: 'row', alignItems: 'flex-start', marginBottom: 12 },
  benefitBullet: { color: GREEN, marginRight: 10, fontWeight: '800', fontSize: 15 },
  benefitText: { fontSize: 15, color: DARK, flex: 1, lineHeight: 21 },
  primaryButton: {
    backgroundColor: INDIGO,
    borderRadius: 14,
    paddingVertical: 15,
    paddingHorizontal: 24,
    alignSelf: 'stretch',
    alignItems: 'center',
  },
  primaryButtonLabel: { fontSize: 16, fontWeight: '700', color: '#FFFFFF' },
  secondaryButton: { paddingVertical: 12, marginTop: 8 },
  secondaryButtonLabel: { fontSize: 14, color: MUTED },
  successIcon: { fontSize: 64, marginBottom: 12 },
  failureIcon: { fontSize: 64, marginBottom: 12 },
});
