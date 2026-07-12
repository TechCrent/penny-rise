import React, { useState } from 'react';
import { ScrollView, View, Text, Pressable, ActivityIndicator, StyleSheet } from 'react-native';
import { useRoute, RouteProp } from '@react-navigation/native';
import { useChallengeDetail, useJoinChallenge } from './useChallenges';
import { BadgePreview } from './components/BadgePreview';
import { JoinConfirmationSheet } from './components/JoinConfirmationSheet';
import { ProgressBar } from '../../components/ProgressBar';
import type { RootStackParamList } from '../../navigation/RootNavigator';

type Route = RouteProp<RootStackParamList, 'ChallengeDetail'>;

export function ChallengeDetailScreen() {
  const route = useRoute<Route>();
  const { challengeId } = route.params;
  const {
    data: challenge,
    isLoading,
    isError,
    refetch,
  } = useChallengeDetail(challengeId);
  const joinMutation = useJoinChallenge();
  const [showConfirmSheet, setShowConfirmSheet] = useState(false);

  if (isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator testID="challenge-detail-loading" />
      </View>
    );
  }

  if (isError || !challenge) {
    return (
      <View style={styles.centered}>
        <Text style={styles.message} testID="challenge-detail-error">
          Couldn&apos;t load this challenge.
        </Text>
        <Pressable style={styles.retryButton} onPress={() => refetch()} testID="challenge-detail-retry">
          <Text style={styles.retryButtonLabel}>Retry</Text>
        </Pressable>
      </View>
    );
  }

  const progressPercent =
    challenge.enrollment && challenge.targetAmount
      ? Math.min(
          100,
          Math.round((challenge.enrollment.progressAmount / challenge.targetAmount) * 100),
        )
      : 0;

  const isCompleted = challenge.enrollment?.status === 'COMPLETED';
  const isActive = challenge.enrollment?.status === 'ACTIVE';
  const isUnenrolled = !challenge.enrollment;

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.container}>
      <View style={styles.badgeSection}>
        <BadgePreview badgeName={challenge.badgeName} size={96} earned={isCompleted} />
        {isCompleted && (
          <Text style={styles.badgeEarnedLabel} testID="badge-earned-label">
            Badge earned!
          </Text>
        )}
      </View>

      <Text style={styles.title}>{challenge.name}</Text>
      <Text style={styles.description}>{challenge.description}</Text>

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

      {isActive && challenge.enrollment && (
        <View style={styles.progressSection} testID="detail-progress-section">
          <Text style={styles.detailLabel}>Progress</Text>
          <ProgressBar percent={progressPercent} />
          <Text style={styles.progressText}>
            GHS {(challenge.enrollment.progressAmount / 100).toFixed(2)}
            {challenge.targetAmount ? ` of GHS ${(challenge.targetAmount / 100).toFixed(2)}` : ''} (
            {progressPercent}%)
          </Text>
          <Text style={styles.detailValueSmall}>
            Enrolled {new Date(challenge.enrollment.enrolledAt).toLocaleDateString('en-GH')}
          </Text>
        </View>
      )}

      {isCompleted && challenge.enrollment?.completedAt && (
        <View style={styles.detailRow}>
          <Text style={styles.detailLabel}>Completed</Text>
          <Text style={styles.detailValue}>
            {new Date(challenge.enrollment.completedAt).toLocaleDateString('en-GH')}
          </Text>
        </View>
      )}

      {isUnenrolled && (
        <Pressable
          style={styles.enrolButton}
          onPress={() => setShowConfirmSheet(true)}
          accessibilityRole="button"
          accessibilityLabel="Join this challenge"
        >
          <Text style={styles.enrolButtonLabel}>Join Challenge</Text>
        </Pressable>
      )}

      <JoinConfirmationSheet
        challenge={showConfirmSheet ? challenge : null}
        isSubmitting={joinMutation.isPending}
        errorMessage={joinMutation.isError ? 'Could not join this challenge. Try again.' : null}
        onConfirm={() => {
          joinMutation.mutate(challenge.id, {
            onSuccess: () => setShowConfirmSheet(false),
          });
        }}
        onCancel={() => setShowConfirmSheet(false)}
      />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#F9FAFB' },
  container: { padding: 20 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32 },
  message: { fontSize: 14, color: '#6B7280', textAlign: 'center', marginBottom: 16 },
  retryButton: {
    backgroundColor: '#1A1A1A',
    borderRadius: 8,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  retryButtonLabel: { color: '#FFFFFF', fontSize: 14, fontWeight: '700' },
  badgeSection: { alignItems: 'center', marginBottom: 20 },
  badgeEarnedLabel: { fontSize: 15, fontWeight: '700', color: '#059669', marginTop: 8 },
  title: { fontSize: 22, fontWeight: '800', color: '#111827', marginBottom: 8 },
  description: { fontSize: 14, color: '#6B7280', marginBottom: 20, lineHeight: 20 },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E5E7EB',
  },
  detailLabel: { fontSize: 14, color: '#6B7280' },
  detailValue: { fontSize: 14, fontWeight: '700', color: '#111827' },
  detailValueSmall: { fontSize: 12, color: '#6B7280', marginTop: 6 },
  progressSection: { marginTop: 20 },
  progressText: { fontSize: 12, color: '#6B7280', marginTop: 6 },
  enrolButton: {
    backgroundColor: '#1A1A1A',
    borderRadius: 8,
    paddingVertical: 14,
    alignItems: 'center',
    marginTop: 28,
  },
  enrolButtonLabel: { fontSize: 15, fontWeight: '700', color: '#FFFFFF' },
});
