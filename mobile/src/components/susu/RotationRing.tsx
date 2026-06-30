import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { SusuMemberSummary } from '../../types/susu';

interface Props {
  members: SusuMemberSummary[];
  currentRecipientUserId: string | null;
  currentRoundNumber: number | null;
  isPending: boolean;
  size?: number;
}

/**
 * Circular rotation ring — one node per member arranged around a circle.
 * - Current recipient: filled dark (primary brand colour)
 * - Completed positions (round_number < current): mid-gray
 * - Future positions: outlined only
 * - PENDING state: all nodes outlined (greyed out)
 */
export function RotationRing({
  members,
  currentRecipientUserId,
  currentRoundNumber,
  isPending,
  size = 240,
}: Props) {
  const sorted = [...members].sort(
    (a, b) => (a.rotation_position ?? 999) - (b.rotation_position ?? 999),
  );

  const radius = size / 2 - 32;
  const nodeSize = 32;
  const center = size / 2;
  const count = sorted.length;

  if (count === 0) return null;

  return (
    <View style={[styles.container, { width: size, height: size }]}>
      {/* Connecting ring line */}
      <View
        style={[
          styles.ringLine,
          isPending ? styles.ringLinePending : styles.ringLineActive,
          {
            width: radius * 2,
            height: radius * 2,
            borderRadius: radius,
            left: center - radius,
            top: center - radius,
          },
        ]}
      />

      {sorted.map((member, idx) => {
        const angle = (idx / count) * 2 * Math.PI - Math.PI / 2;
        const x = center + radius * Math.cos(angle) - nodeSize / 2;
        const y = center + radius * Math.sin(angle) - nodeSize / 2;
        const pos = member.rotation_position ?? 0;
        const isCurrent = member.user_id === currentRecipientUserId && !isPending;
        const isCompleted = !isPending && currentRoundNumber !== null && pos < currentRoundNumber;

        const bgColor = isPending
          ? '#F3F4F6'
          : isCurrent
            ? '#111827'
            : isCompleted
              ? '#9CA3AF'
              : 'transparent';
        const borderColor = isPending
          ? '#D1D5DB'
          : isCurrent
            ? '#111827'
            : isCompleted
              ? '#9CA3AF'
              : '#374151';
        const textColor = isCurrent || isCompleted ? '#FFFFFF' : '#374151';

        const labelX = center + (radius + 24) * Math.cos(angle);
        const labelY = center + (radius + 24) * Math.sin(angle);
        const labelLeft = labelX - 36;
        const labelTop = labelY - 10;

        return (
          <React.Fragment key={member.user_id}>
            <View
              style={[
                styles.node,
                {
                  left: x,
                  top: y,
                  width: nodeSize,
                  height: nodeSize,
                  borderRadius: nodeSize / 2,
                  backgroundColor: bgColor,
                  borderColor,
                },
              ]}
            >
              <Text style={[styles.nodeText, { color: textColor }]}>{pos > 0 ? pos : '?'}</Text>
            </View>

            <Text style={[styles.label, { left: labelLeft, top: labelTop }]} numberOfLines={1}>
              {member.display_name.split(' ')[0]}
            </Text>
          </React.Fragment>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    position: 'relative',
    alignSelf: 'center',
  },
  ringLine: {
    position: 'absolute',
    borderWidth: 1.5,
  },
  ringLinePending: { borderColor: '#D1D5DB' },
  ringLineActive: { borderColor: '#374151' },
  node: {
    position: 'absolute',
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nodeText: {
    fontSize: 12,
    fontWeight: '700',
  },
  label: {
    position: 'absolute',
    fontSize: 10,
    color: '#6B7280',
    width: 72,
    textAlign: 'center',
  },
});
