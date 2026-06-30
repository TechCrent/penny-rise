import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import type { TransactionType } from '../../types/wallet';

interface BadgeConfig {
  label: string;
  bg: string;
  text: string;
  icon: string;
}

const CONFIG: Record<TransactionType, BadgeConfig> = {
  DEPOSIT: { label: 'Deposit', bg: '#D1FAE5', text: '#065F46', icon: '↓' },
  WITHDRAWAL: { label: 'Withdrawal', bg: '#FEE2E2', text: '#991B1B', icon: '↑' },
  PEER_TRANSFER: { label: 'Transfer', bg: '#DBEAFE', text: '#1E40AF', icon: '↔️' },
  PEER_TRANSFER_FEE: { label: 'Fee', bg: '#F3F4F6', text: '#374151', icon: '⬤' },
  SUSU_CONTRIBUTION: { label: 'Susu', bg: '#EDE9FE', text: '#5B21B6', icon: '⬆' },
  SUSU_DISBURSEMENT: { label: 'Susu payout', bg: '#D1FAE5', text: '#065F46', icon: '⬇' },
  VAULT_DEPOSIT: { label: 'To vault', bg: '#FEF3C7', text: '#92400E', icon: '→' },
  VAULT_WITHDRAWAL: { label: 'From vault', bg: '#FEF3C7', text: '#92400E', icon: '←' },
  EARLY_EXIT_PENALTY: { label: 'Penalty', bg: '#FEE2E2', text: '#991B1B', icon: '!' },
  SUSU_PENALTY: { label: 'Penalty', bg: '#FEE2E2', text: '#991B1B', icon: '!' },
  OTHER: { label: 'Activity', bg: '#F3F4F6', text: '#374151', icon: '·' },
};

interface Props {
  type: TransactionType;
}

export function TransactionBadge({ type }: Props) {
  const config = CONFIG[type] ?? CONFIG.OTHER;
  return (
    <View style={[styles.badge, { backgroundColor: config.bg }]} testID={`badge-${type}`}>
      <Text style={[styles.icon, { color: config.text }]}>{config.icon}</Text>
      <Text style={[styles.label, { color: config.text }]}>{config.label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 10,
    gap: 4,
  },
  icon: { fontSize: 11, fontWeight: '700' },
  label: { fontSize: 11, fontWeight: '600' },
});
