import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, radii } from '../../theme';

type Status = 'PAID' | 'PENDING' | 'LATE' | 'MISSED' | 'WAIVED';

const CONFIG: Record<Status, { label: string; bg: string; text: string }> = {
  PAID: { label: 'Paid', bg: colors.status.successBg, text: colors.status.successText },
  PENDING: { label: 'Pending', bg: colors.status.warningBg, text: colors.status.warningText },
  LATE: { label: 'Late', bg: colors.status.errorBg, text: colors.status.errorText },
  MISSED: { label: 'Missed', bg: colors.neutral[100], text: colors.neutral[700] },
  WAIVED: { label: 'Waived', bg: colors.status.infoBg, text: colors.status.infoText },
};

export function ContributionStatusPill({ status }: { status: Status }) {
  const { label, bg, text } = CONFIG[status] ?? CONFIG['PENDING'];
  return (
    <View style={[styles.pill, { backgroundColor: bg }]}>
      <Text style={[styles.text, { color: text }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: radii.pill,
  },
  text: {
    fontSize: 11,
    fontWeight: '600',
  },
});
