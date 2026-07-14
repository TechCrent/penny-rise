import React, { useEffect, useRef, useCallback, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, Linking, ScrollView } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated from 'react-native-reanimated';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { getSubmissionStatus } from '../api/kyc';
import {
  clearKycSubmission,
  markKycApprovalAcknowledged,
  markKycUnderReviewBannerPending,
  clearKycUnderReviewBannerPending,
} from '../storage/kycStorage';
import { supportMailtoUrl } from '../constants/support';
import { Banner, GradientHero, PressableScale, fadeInUp } from '../components/ui';
import { colors, radii, spacing, typography, shadows } from '../theme';

type Nav = NativeStackNavigationProp<RootStackParamList, 'KycSubmissionPending'>;
type Route = RouteProp<RootStackParamList, 'KycSubmissionPending'>;

const POLL_INTERVAL_MS = 10_000;

type ScreenState =
  | { kind: 'loading' }
  | { kind: 'under_review' }
  | { kind: 'approved' }
  | { kind: 'rejected'; reason: string | null };

export default function KycSubmissionPendingScreen() {
  const navigation = useNavigation<Nav>();
  const route = useRoute<Route>();
  const { submissionId } = route.params;

  const [screenState, setScreenState] = useState<ScreenState>({ kind: 'loading' });
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = useCallback(() => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  }, []);

  const handleApproved = useCallback(async () => {
    stopPolling();
    await clearKycSubmission();
    await markKycApprovalAcknowledged();
    await clearKycUnderReviewBannerPending();
    setScreenState({ kind: 'approved' });

    setTimeout(() => {
      navigation.reset({ index: 0, routes: [{ name: 'Main', params: { screen: 'Home' } }] });
    }, 2000);
  }, [stopPolling, navigation]);

  const handleRejected = useCallback(
    async (reason: string | null) => {
      stopPolling();
      await clearKycSubmission();
      await clearKycUnderReviewBannerPending();
      setScreenState({ kind: 'rejected', reason });
    },
    [stopPolling],
  );

  const poll = useCallback(async () => {
    try {
      const status = await getSubmissionStatus(submissionId);

      switch (status.status) {
        case 'APPROVED':
          await handleApproved();
          break;
        case 'REJECTED':
          await handleRejected(status.rejection_reason);
          break;
        case 'REVIEWING':
        case 'SUBMITTED':
          void markKycUnderReviewBannerPending();
          setScreenState({ kind: 'under_review' });
          break;
        default:
          void markKycUnderReviewBannerPending();
          setScreenState({ kind: 'under_review' });
      }
    } catch (err) {
      console.error(err);
      // Network error — retry on next poll
    }
  }, [submissionId, handleApproved, handleRejected]);

  useEffect(() => {
    poll();
    pollRef.current = setInterval(poll, POLL_INTERVAL_MS);
    return stopPolling;
  }, [poll, stopPolling]);

  const openSupportEmail = () => {
    Linking.openURL(supportMailtoUrl('KYC verification help'));
  };

  if (screenState.kind === 'loading') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={colors.gold.base} />
          <Text style={styles.loadingText}>Checking submission status…</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (screenState.kind === 'approved') {
    return (
      <SafeAreaView style={styles.safe}>
        <ScrollView
          contentContainerStyle={styles.approvedScroll}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <Animated.View entering={fadeInUp(40)}>
            <GradientHero
              icon="checkmark-circle"
              title="Identity approved!"
              subtitle="Your KYC is approved. Taking you to PennyRise…"
            />
          </Animated.View>
          <View style={styles.approvedSpinner}>
            <ActivityIndicator size="large" color={colors.gold.base} />
          </View>
        </ScrollView>
      </SafeAreaView>
    );
  }

  if (screenState.kind === 'rejected') {
    return (
      <SafeAreaView style={styles.safe}>
        <ScrollView
          contentContainerStyle={styles.scroll}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <Animated.View entering={fadeInUp(40)}>
            <GradientHero icon="close-circle" title="Verification unsuccessful" />
          </Animated.View>

          <Animated.View entering={fadeInUp(110)} style={styles.contentCard}>
            {screenState.reason ? (
              <View style={styles.bannerWrap}>
                <Banner tone="error" title="Reason:" message={screenState.reason} />
              </View>
            ) : null}
            <Text style={styles.body}>
              If you believe this is a mistake or need help, please contact our support team.
            </Text>
            <PressableScale style={styles.supportButton} onPress={openSupportEmail}>
              <Text style={styles.supportButtonText}>Contact support</Text>
            </PressableScale>
            <PressableScale
              style={styles.retryButton}
              onPress={async () => {
                await clearKycSubmission();
                navigation.replace('KycCardDetails');
              }}
            >
              <Text style={styles.retryButtonText}>Try again with a new submission</Text>
            </PressableScale>
          </Animated.View>
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <ScrollView
        contentContainerStyle={styles.scroll}
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      >
        <Animated.View entering={fadeInUp(40)}>
          <GradientHero
            icon="shield-checkmark-outline"
            title="Under review"
            subtitle="Step 3 of 3"
          />
        </Animated.View>

        <Animated.View entering={fadeInUp(110)} style={styles.contentCard}>
          <Text style={styles.body}>
            Your documents are being reviewed. This usually takes a few minutes, but can
            occasionally take up to 24 hours.
          </Text>
          <Text style={styles.bodySecondary}>
            You don&apos;t need to keep this screen open — we&apos;ll notify you when a decision is
            made.
          </Text>

          <View style={styles.bannerWrap}>
            <Banner
              tone="info"
              icon="shield-checkmark-outline"
              message="Your documents are handled securely and reviewed by our team."
            />
          </View>

          <View style={styles.timeframeBox}>
            <Text style={styles.timeframeLabel}>Typical review time</Text>
            <Text style={styles.timeframeValue}>Under 2 minutes</Text>
          </View>

          <View style={styles.spinner}>
            <ActivityIndicator size="large" color={colors.gold.base} />
          </View>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  scroll: {
    flexGrow: 1,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  approvedScroll: {
    flexGrow: 1,
    justifyContent: 'center',
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    paddingBottom: spacing['4xl'],
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: spacing.xl,
  },
  loadingText: { marginTop: spacing.lg, fontSize: 16, color: colors.textSecondary },
  approvedSpinner: { alignItems: 'center', marginTop: spacing.xl },
  contentCard: {
    backgroundColor: colors.surface,
    borderRadius: radii['2xl'],
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.xl,
    ...shadows.sm,
  },
  spinner: { alignItems: 'center', marginTop: spacing['2xl'] },
  body: {
    fontSize: 15,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: spacing.lg,
  },
  bodySecondary: {
    fontSize: 14,
    color: colors.textTertiary,
    textAlign: 'center',
    lineHeight: 22,
    marginBottom: spacing.lg,
  },
  bannerWrap: { alignSelf: 'stretch', marginBottom: spacing.lg },
  timeframeBox: {
    backgroundColor: colors.background,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.xl,
    padding: spacing.xl,
    alignItems: 'center',
    width: '100%',
  },
  timeframeLabel: { fontSize: 13, color: colors.textSecondary, marginBottom: spacing.xxs },
  timeframeValue: { ...typography.h3, color: colors.textPrimary },
  supportButton: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing['3xl'],
    marginTop: spacing.lg,
    alignSelf: 'center',
  },
  supportButtonText: { fontSize: 15, fontWeight: '600', color: colors.neutral[900] },
  retryButton: { marginTop: spacing.md, padding: spacing.sm, alignSelf: 'center' },
  retryButtonText: { color: colors.textSecondary, fontSize: 14, textDecorationLine: 'underline' },
});
