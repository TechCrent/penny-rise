import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import type { SusuGroupListResponse } from '../../types/susu';

interface Props {
  group: SusuGroupListResponse;
  onPress: () => void;
}

function formatFrequency(f: string) {
  return f === 'BIWEEKLY' ? 'Every 2 wks'
       : f === 'WEEKLY'   ? 'Weekly'
       : 'Monthly';
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

  const dueDate  = formatDueDate(group.next_due_date);
  const isPending = group.status === 'PENDING';

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      activeOpacity={0.85}
      testID={`susu-card-${group.group_id}`}
    >
      {/* Header row */}
      <View style={styles.headerRow}>
        <Text style={styles.name} numberOfLines={1}>{group.name}</Text>
        {group.caller_is_next_recipient && (
          <View style={styles.youreNextPill} testID="youre-next-pill">
            <Text style={styles.youreNextText}>{"You're next"}</Text>
          </View>
        )}
      </View>

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
      {dueDate && !isPending && (
        <Text style={styles.dueDate}>Due {dueDate}</Text>
      )}

      {/* Progress bar */}
      {!isPending && group.total_rounds && (
        <View style={styles.progressBg}>
          <View style={[styles.progressFill, { width: `${progressFraction * 100}%` }]} />
        </View>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius:    12,
    padding:         16,
    marginBottom:    12,
    shadowColor:     '#000',
    shadowOpacity:   0.06,
    shadowRadius:    8,
    shadowOffset:    { width: 0, height: 2 },
    elevation:       2,
  },
  headerRow: {
    flexDirection:  'row',
    justifyContent: 'space-between',
    alignItems:     'center',
    marginBottom:    4,
  },
  name: {
    fontSize:    16,
    fontWeight:  '700',
    color:       '#111827',
    flex:         1,
    marginRight:  8,
  },
  youreNextPill: {
    backgroundColor:  '#FEF3C7',
    paddingHorizontal: 8,
    paddingVertical:   3,
    borderRadius:     12,
  },
  youreNextText: {
    fontSize:   11,
    fontWeight: '700',
    color:      '#92400E',
  },
  metaRow: {
    flexDirection:  'row',
    justifyContent: 'space-between',
    marginBottom:    2,
  },
  meta: {
    fontSize:   13,
    color:      '#374151',
    fontWeight: '500',
  },
  metaSecondary: {
    fontSize: 12,
    color:    '#6B7280',
  },
  dueDate: {
    fontSize:   12,
    color:      '#EF4444',
    fontWeight: '500',
    marginTop:   4,
  },
  progressBg: {
    height:          4,
    backgroundColor: '#F3F4F6',
    borderRadius:    2,
    marginTop:       10,
    overflow:        'hidden',
  },
  progressFill: {
    height:          4,
    backgroundColor: '#111827',
    borderRadius:    2,
  },
});
