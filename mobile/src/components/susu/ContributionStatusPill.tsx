import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

type Status = 'PAID' | 'PENDING' | 'LATE' | 'MISSED' | 'WAIVED';

const CONFIG: Record<Status, { label: string; bg: string; text: string }> = {
  PAID: { label: 'Paid', bg: '#D1FAE5', text: '#065F46' },
  PENDING: { label: 'Pending', bg: '#FEF3C7', text: '#92400E' },
  LATE: { label: 'Late', bg: '#FEE2E2', text: '#991B1B' },
  MISSED: { label: 'Missed', bg: '#F3F4F6', text: '#374151' },
  WAIVED: { label: 'Waived', bg: '#EDE9FE', text: '#5B21B6' },
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
    borderRadius: 12,
  },
  text: {
    fontSize: 11,
    fontWeight: '600',
  },
});
