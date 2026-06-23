import React, { useEffect, useRef, useCallback, useState } from 'react';
import { View, Text, StyleSheet, ActivityIndicator, TouchableOpacity, Linking } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';

import type { RootStackParamList } from '../navigation/RootNavigator';
import { getSubmissionStatus } from '../api/kyc';
import { clearKycSubmission } from '../storage/kycStorage';
import { supportMailtoUrl } from '../constants/support';

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
    setScreenState({ kind: 'approved' });

    setTimeout(() => {
      navigation.reset({ index: 0, routes: [{ name: 'Home' }] });
    }, 2000);
  }, [stopPolling, navigation]);

  const handleRejected = useCallback(
    (reason: string | null) => {
      stopPolling();
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
          handleRejected(status.rejection_reason);
          break;
        case 'REVIEWING':
        case 'SUBMITTED':
          setScreenState({ kind: 'under_review' });
          break;
        default:
          setScreenState({ kind: 'under_review' });
      }
    } catch {
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
          <ActivityIndicator size="large" color="#1A1A1A" />
          <Text style={styles.loadingText}>Checking submission status…</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (screenState.kind === 'approved') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Text style={styles.successEmoji}>Approved</Text>
          <Text style={styles.heading}>Identity approved!</Text>
          <Text style={styles.body}>Your KYC is approved. Taking you to Stash…</Text>
        </View>
      </SafeAreaView>
    );
  }

  if (screenState.kind === 'rejected') {
    return (
      <SafeAreaView style={styles.safe}>
        <View style={styles.centered}>
          <Text style={styles.rejectedEmoji}>Unsuccessful</Text>
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
          <TouchableOpacity style={styles.supportButton} onPress={openSupportEmail}>
            <Text style={styles.supportButtonText}>Contact support</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.retryButton}
            onPress={() => navigation.replace('KycCardDetails')}
          >
            <Text style={styles.retryButtonText}>Try again with a new submission</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safe}>
      <View style={styles.centered}>
        <View style={styles.spinner}>
          <ActivityIndicator size="large" color="#1A1A1A" />
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
  safe: { flex: 1, backgroundColor: '#FFFFFF' },
  centered: {
    flex: 1,
    paddingHorizontal: 32,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: { marginTop: 16, fontSize: 15, color: '#6B7280' },
  successEmoji: { fontSize: 20, fontWeight: '700', color: '#059669', marginBottom: 20 },
  rejectedEmoji: { fontSize: 20, fontWeight: '700', color: '#991B1B', marginBottom: 20 },
  spinner: { marginBottom: 24 },
  heading: {
    fontSize: 26,
    fontWeight: '700',
    color: '#111827',
    marginBottom: 8,
    textAlign: 'center',
  },
  stepText: { fontSize: 13, color: '#9CA3AF', marginBottom: 16 },
  body: {
    fontSize: 15,
    color: '#6B7280',
    textAlign: 'center',
    lineHeight: 24,
    marginBottom: 16,
  },
  bodySecondary: { fontSize: 14, color: '#9CA3AF', textAlign: 'center', lineHeight: 22 },
  timeframeBox: {
    marginTop: 32,
    backgroundColor: '#F9FAFB',
    borderRadius: 10,
    padding: 20,
    alignItems: 'center',
    width: '100%',
  },
  timeframeLabel: { fontSize: 13, color: '#6B7280', marginBottom: 4 },
  timeframeValue: { fontSize: 18, fontWeight: '700', color: '#111827' },
  reasonBox: {
    backgroundColor: '#FEF2F2',
    borderRadius: 8,
    padding: 14,
    marginBottom: 16,
    width: '100%',
  },
  reasonLabel: { fontSize: 13, fontWeight: '600', color: '#991B1B', marginBottom: 4 },
  reasonText: { fontSize: 14, color: '#991B1B', lineHeight: 20 },
  supportButton: {
    backgroundColor: '#1A1A1A',
    borderRadius: 8,
    paddingVertical: 14,
    paddingHorizontal: 32,
    marginTop: 20,
  },
  supportButtonText: { color: '#FFFFFF', fontSize: 15, fontWeight: '600' },
  retryButton: { marginTop: 16, padding: 12 },
  retryButtonText: { color: '#6B7280', fontSize: 14, textDecorationLine: 'underline' },
});
