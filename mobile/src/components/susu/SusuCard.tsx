import React, { useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import type { SusuGroupListResponse } from '../../types/susu';
import { PressableScale } from '../ui';
import { colors, radii, shadows, spacing, typography } from '../../theme';

interface Props {
  group: SusuGroupListResponse;
  onPress: () => void;
}

function formatFrequency(f: string) {
  return f === 'BIWEEKLY' ? 'Every 2 wks' : f === 'WEEKLY' ? 'Weekly' : 'Monthly';
}

function formatDueDate(isoDate: string | null) {
  if (!isoDate) return null;
  const d = new Date(isoDate);
  return d.toLocaleDateString('en-GH', { day: 'numeric', month: 'short' });
}

export function SusuCard({ group, onPress }: Props) {
  const progressFraction =
    group.total_rounds && group.current_round_number
      ? (group.current_round_number - 1) / group.total_rounds
      : 0;

  const dueDate = formatDueDate(group.next_due_date);
  const isPending = group.status === 'PENDING';
  const isFrozen = group.status === 'FROZEN';

  return (
    <PressableScale
      style={styles.card}
      onPress={onPress}
      testID={`susu-card-${group.group_id}`}
      accessibilityRole="button"
      accessibilityLabel={`${group.name} susu group`}
    >
      {/* Header row */}
      <View style={styles.headerRow}>
        <Text style={styles.name} numberOfLines={1}>
          {group.name}
        </Text>
        {group.caller_is_next_recipient && (
          <View style={styles.youreNextPill} testID="youre-next-pill">
            <Text style={styles.youreNextText}>{"You're next"}</Text>
          </View>
        )}
        {isFrozen && (
          <View style={styles.frozenPill} testID={`susu-frozen-badge-${group.group_id}`}>
            <Text style={styles.frozenText}>FROZEN</Text>
          </View>
        )}
      </View>

      {isFrozen && (
        <Text style={styles.frozenNotice}>
          Over the organiser&apos;s plan limit — new contributions are blocked until they upgrade.
        </Text>
      )}

      {/* Meta row */}
      <View style={styles.metaRow}>
        <Text style={styles.meta}>
          {group.contribution_amount_cedis} · {formatFrequency(group.frequency)}
        </Text>
        <Text style={styles.meta}>
          {group.current_member_count}/{group.target_member_count} members
        </Text>
      </View>

      {/* Round / position row */}
      <View style={styles.metaRow}>
        {isPending ? (
          <Text style={styles.metaSecondary}>Waiting to start</Text>
        ) : (
          <>
            <Text style={styles.metaSecondary}>
              Round {group.current_round_number} of {group.total_rounds}
            </Text>
            {group.caller_rotation_position && (
              <Text style={styles.metaSecondary}>
                Your position: #{group.caller_rotation_position}
              </Text>
            )}
          </>
        )}
      </View>

      {/* Due date */}
      {dueDate && !isPending && <Text style={styles.dueDate}>Due {dueDate}</Text>}

      {/* Progress bar */}
      {!isPending && group.total_rounds && <ProgressTrack fraction={progressFraction} />}
    </PressableScale>
  );
}

function ProgressTrack({ fraction }: { fraction: number }) {
  const width = useSharedValue(0);

  useEffect(() => {
    width.value = withTiming(fraction * 100, {
      duration: 700,
      easing: Easing.out(Easing.cubic),
    });
  }, [fraction, width]);

  const animatedStyle = useAnimatedStyle(() => ({
    width: `${width.value}%`,
  }));

  return (
    <View style={styles.progressBg}>
      <Animated.View style={[styles.progressFill, animatedStyle]} />
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
    ...shadows.sm,
  },
  headerRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.xs,
  },
  name: {
    ...typography.h3,
    color: colors.textPrimary,
    flex: 1,
    marginRight: spacing.sm,
  },
  youreNextPill: {
    backgroundColor: colors.gold.light,
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
    borderRadius: radii.pill,
  },
  youreNextText: {
    fontSize: 11,
    fontWeight: '700',
    color: colors.gold.text,
  },
  frozenPill: {
    backgroundColor: colors.status.infoBg,
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
    borderRadius: radii.pill,
  },
  frozenText: {
    fontSize: 11,
    fontWeight: '700',
    color: colors.status.infoText,
  },
  frozenNotice: {
    fontSize: 12,
    color: colors.status.infoText,
    marginTop: spacing.xs,
    lineHeight: 17,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 2,
  },
  meta: {
    fontSize: 13,
    color: colors.neutral[700],
    fontWeight: '500',
  },
  metaSecondary: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  dueDate: {
    fontSize: 12,
    color: colors.status.error,
    fontWeight: '500',
    marginTop: spacing.xs,
  },
  progressBg: {
    height: 4,
    backgroundColor: colors.neutral[100],
    borderRadius: 2,
    marginTop: spacing.sm,
    overflow: 'hidden',
  },
  progressFill: {
    height: 4,
    backgroundColor: colors.gold.base,
    borderRadius: 2,
  },
});
