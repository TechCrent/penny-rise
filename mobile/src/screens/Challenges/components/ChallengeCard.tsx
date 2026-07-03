import React from 'react';
import { Pressable, View, Text, StyleSheet } from 'react-native';
import { BadgePreview } from './BadgePreview';
import { ProgressBar } from '../../../components/ProgressBar';
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
    <Pressable
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
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    padding: 14,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    marginBottom: 10,
    marginHorizontal: 16,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E5E7EB',
  },
  info: { flex: 1, marginLeft: 14 },
  title: { fontSize: 15, fontWeight: '700', color: '#111827' },
  meta: { fontSize: 12, color: '#6B7280', marginTop: 2 },
  progressContainer: { marginTop: 8 },
  progressLabel: { fontSize: 11, color: '#6B7280', marginTop: 4 },
  completedLabel: { fontSize: 12, color: '#059669', marginTop: 6, fontWeight: '600' },
});
