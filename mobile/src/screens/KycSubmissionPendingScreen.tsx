import React, { useEffect, useRef, useCallback, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, Linking } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import Animated, { FadeIn } from 'react-native-reanimated';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { getSubmissionStatus } from '../api/kyc';
import {
  clearKycSubmission,
  markKycApprovalAcknowledged,
  markKycUnderReviewBannerPending,
  clearKycUnderReviewBannerPending,
} from '../storage/kycStorage';
import { supportMailtoUrl } from '../constants/support';
import { Icon, PressableScale } from '../components/ui';
import { colors, radii, spacing, typography } from '../theme';

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
        <View style={styles.centered}>
          <ActivityIndicator size="large" color={colors.gold.base} />
          <Text style={styles.loadingText}>Checking submission status…</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (screenState.kind === 'approved') {
    return (
      <SafeAreaView style={styles.safe}>
        <Animated.View entering={FadeIn.duration(400)} style={styles.centered}>
          <View style={[styles.resultIconBadge, styles.successIconBadge]}>
            <Icon name="checkmark-circle" size={40} color={colors.status.success} />
          </View>
          <Text style={styles.heading}>Identity approved!</Text>
          <Text style={styles.body}>Your KYC is approved. Taking you to Stash…</Text>
        </Animated.View>
      </SafeAreaView>
    );
  }

  if (screenState.kind === 'rejected') {
    return (
      <SafeAreaView style={styles.safe}>
        <Animated.View entering={FadeIn.duration(400)} style={styles.centered}>
          <View style={[styles.resultIconBadge, styles.errorIconBadge]}>
            <Icon name="close-circle" size={40} color={colors.status.error} />
          </View>
          <Text style={styles.heading}>Verification unsuccessful</Text>
          {screenState.reason ? (
            <View style={styles.reasonBox}>
              <Text style={styles.reasonLabel}>Reason:</Text>
              <Text style={styles.reasonText}>{screenState.reason}</Text>
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
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.centered}>
        <View style={styles.spinner}>
          <ActivityIndicator size="large" color={colors.gold.base} />
        </View>
        <Text style={styles.heading}>Under review</Text>
        <Text style={styles.stepText}>Step 3 of 3</Text>
        <Text style={styles.body}>
          Your documents are being reviewed. This usually takes a few minutes, but can occasionally
          take up to 24 hours.
        </Text>
        <Text style={styles.bodySecondary}>
          You don&apos;t need to keep this screen open — we&apos;ll notify you when a decision is
          made.
        </Text>
        <View style={styles.timeframeBox}>
          <Text style={styles.timeframeLabel}>Typical review time</Text>
          <Text style={styles.timeframeValue}>Under 2 minutes</Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.background },
  centered: {
    flex: 1,
    paddingHorizontal: spacing['3xl'],
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: { marginTop: spacing.lg, fontSize: 15, color: colors.textSecondary },
  resultIconBadge: {
    width: 72,
    height: 72,
    borderRadius: radii.pill,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.xl,
  },
  successIconBadge: { backgroundColor: colors.status.successBg },
  errorIconBadge: { backgroundColor: colors.status.errorBg },
  spinner: { marginBottom: spacing['2xl'] },
  heading: {
    ...typography.h1,
    fontSize: 26,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
    textAlign: 'center',
  },
  stepText: { fontSize: 13, color: colors.textTertiary, marginBottom: spacing.lg },
  body: {
    fontSize: 15,
    color: colors.textSecondary,
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: spacing.lg,
  },
  bodySecondary: { fontSize: 14, color: colors.textTertiary, textAlign: 'center', lineHeight: 22 },
  timeframeBox: {
    marginTop: spacing['3xl'],
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    padding: spacing.xl,
    alignItems: 'center',
    width: '100%',
  },
  timeframeLabel: { fontSize: 13, color: colors.textSecondary, marginBottom: spacing.xxs },
  timeframeValue: { ...typography.h3, color: colors.textPrimary },
  reasonBox: {
    backgroundColor: colors.status.errorBg,
    borderRadius: radii.sm,
    padding: spacing.md,
    marginBottom: spacing.lg,
    width: '100%',
  },
  reasonLabel: { fontSize: 13, fontWeight: '600', color: colors.status.errorText, marginBottom: spacing.xxs },
  reasonText: { fontSize: 14, color: colors.status.errorText, lineHeight: 20 },
  supportButton: {
    backgroundColor: colors.gold.base,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing['3xl'],
    marginTop: spacing.lg,
  },
  supportButtonText: { fontSize: 15, fontWeight: '600', color: colors.neutral[900] },
  retryButton: { marginTop: spacing.md, padding: spacing.sm },
  retryButtonText: { color: colors.textSecondary, fontSize: 14, textDecorationLine: 'underline' },
});
