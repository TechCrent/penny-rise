import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { BadgePreview } from './BadgePreview';
import { ProgressBar } from '../../../components/ProgressBar';
import { PressableScale } from '../../../components/ui';
import { colors, radii, shadows, spacing, typography } from '../../../theme';
import { sectionFor } from '../types';
import type { Challenge } from '../types';

interface Props {
  challenge: Challenge;
  onPress: (challenge: Challenge) => void;
}

export function ChallengeCard({ challenge, onPress }: Props) {
  const section = sectionFor(challenge);
  const progressPercent =
    challenge.enrollment && challenge.targetAmount
      ? Math.min(
          100,
          Math.round((challenge.enrollment.progressAmount / challenge.targetAmount) * 100),
        )
      : 0;

  return (
    <PressableScale
      onPress={() => onPress(challenge)}
      style={styles.card}
      accessibilityRole="button"
      accessibilityLabel={`${challenge.name} challenge, ${section.toLowerCase()}`}
      testID={`challenge-card-${challenge.id}`}
    >
      <BadgePreview badgeName={challenge.badgeName} size={48} earned={section === 'COMPLETED'} />

      <View style={styles.info}>
        <Text style={styles.title} numberOfLines={1}>
          {challenge.name}
        </Text>
        <Text style={styles.meta}>
          {challenge.targetAmount ? `GHS ${(challenge.targetAmount / 100).toFixed(0)} · ` : ''}
          {challenge.targetDurationDays} days
        </Text>

        {section === 'ACTIVE' && challenge.enrollment && (
          <View style={styles.progressContainer} testID="challenge-progress-bar">
            <ProgressBar percent={progressPercent} />
            <Text style={styles.progressLabel}>{progressPercent}% complete</Text>
          </View>
        )}

        {section === 'COMPLETED' && challenge.enrollment?.completedAt && (
          <Text style={styles.completedLabel} testID="challenge-completed-label">
            Completed {new Date(challenge.enrollment.completedAt).toLocaleDateString('en-GH')}
          </Text>
        )}
      </View>
    </PressableScale>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    padding: spacing.md,
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    marginBottom: spacing.sm,
    marginHorizontal: spacing.lg,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.sm,
  },
  info: { flex: 1, marginLeft: spacing.md },
  title: { ...typography.bodyMedium, color: colors.textPrimary },
  meta: { ...typography.caption, color: colors.textSecondary, marginTop: 2 },
  progressContainer: { marginTop: spacing.sm },
  progressLabel: { fontSize: 11, color: colors.textSecondary, marginTop: spacing.xs },
  completedLabel: {
    fontSize: 12,
    color: colors.status.success,
    marginTop: spacing.sm,
    fontWeight: '600',
  },
});
