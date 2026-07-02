import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { FilterTab } from '../types';

const EMPTY_COPY: Record<FilterTab, { title: string; subtitle: string }> = {
  ALL: {
    title: 'No transactions yet',
    subtitle: 'Once you deposit, withdraw, or transfer, your activity will show up here.',
  },
  DEPOSIT: { title: 'No deposits yet', subtitle: 'Make your first deposit to start saving.' },
  WITHDRAWAL: {
    title: 'No withdrawals yet',
    subtitle: 'Your withdrawals will appear here once made.',
  },
  TRANSFER: {
    title: 'No transfers yet',
    subtitle: 'Send money to another Stash user to see transfers here.',
  },
  SUSU: {
    title: 'No susu activity yet',
    subtitle: 'Join a susu group and make a contribution to see it here.',
  },
};

export function TransactionEmptyState({ activeTab }: { activeTab: FilterTab }) {
  const { title, subtitle } = EMPTY_COPY[activeTab];
  return (
    <View style={styles.container} testID="empty-state">
      <Text style={styles.title}>{title}</Text>
      <Text style={styles.subtitle}>{subtitle}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 32 },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: '#111827',
    textAlign: 'center',
    marginBottom: 8,
  },
  subtitle: { fontSize: 14, color: '#6B7280', textAlign: 'center' },
});
