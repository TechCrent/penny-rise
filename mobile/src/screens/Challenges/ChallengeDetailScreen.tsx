import React, { useState } from 'react';
import { ScrollView, View, Text, ActivityIndicator, StyleSheet } from 'react-native';
import { useRoute, RouteProp } from '@react-navigation/native';
import { useChallengeDetail, useJoinChallenge } from './useChallenges';
import { BadgePreview } from './components/BadgePreview';
import { JoinConfirmationSheet } from './components/JoinConfirmationSheet';
import { ProgressBar } from '../../components/ProgressBar';
import { PrimaryButton } from '../../components/PrimaryButton';
import { colors, spacing, typography } from '../../theme';
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
        <ActivityIndicator testID="challenge-detail-loading" color={colors.gold.base} />
      </View>
    );
  }

  if (isError || !challenge) {
    return (
      <View style={styles.centered}>
        <Text style={styles.message} testID="challenge-detail-error">
          Couldn&apos;t load this challenge.
        </Text>
        <PrimaryButton
          title="Retry"
          onPress={() => refetch()}
          style={styles.retryButton}
          testID="challenge-detail-retry"
        />
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
        <PrimaryButton
          title="Join Challenge"
          onPress={() => setShowConfirmSheet(true)}
          style={styles.enrolButton}
          accessibilityLabel="Join this challenge"
        />
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
  screen: { flex: 1, backgroundColor: colors.background },
  container: { padding: spacing.xl },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing['3xl'] },
  message: { fontSize: 14, color: colors.textSecondary, textAlign: 'center', marginBottom: spacing.md },
  retryButton: { paddingHorizontal: spacing.xl },
  badgeSection: { alignItems: 'center', marginBottom: spacing.xl },
  badgeEarnedLabel: { fontSize: 15, fontWeight: '700', color: colors.status.successText, marginTop: spacing.sm },
  title: { ...typography.h2, color: colors.textPrimary, marginBottom: spacing.sm },
  description: { fontSize: 14, color: colors.textSecondary, marginBottom: spacing.xl, lineHeight: 20 },
  detailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingVertical: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.border,
  },
  detailLabel: { fontSize: 14, color: colors.textSecondary },
  detailValue: { fontSize: 14, fontWeight: '700', color: colors.textPrimary },
  detailValueSmall: { fontSize: 12, color: colors.textSecondary, marginTop: spacing.xs },
  progressSection: { marginTop: spacing.xl },
  progressText: { fontSize: 12, color: colors.textSecondary, marginTop: spacing.xs },
  enrolButton: { marginTop: spacing['2xl'] },
});
