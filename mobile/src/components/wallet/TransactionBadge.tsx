import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { Icon, type IconName } from '../ui';
import type { TransactionType } from '../../types/wallet';
import { colors, radii } from '../../theme';

interface BadgeConfig {
  label: string;
  bg: string;
  text: string;
  icon: IconName;
}

const CONFIG: Record<TransactionType, BadgeConfig> = {
  DEPOSIT: {
    label: 'Deposit',
    bg: colors.status.successBg,
    text: colors.status.successText,
    icon: 'arrow-down-circle-outline',
  },
  WITHDRAWAL: {
    label: 'Withdrawal',
    bg: colors.status.errorBg,
    text: colors.status.errorText,
    icon: 'arrow-up-circle-outline',
  },
  PEER_TRANSFER: {
    label: 'Transfer',
    bg: colors.status.infoBg,
    text: colors.status.infoText,
    icon: 'swap-horizontal-outline',
  },
  PEER_TRANSFER_FEE: {
    label: 'Fee',
    bg: colors.neutral[100],
    text: colors.neutral[700],
    icon: 'ellipse-outline',
  },
  SUSU_CONTRIBUTION: {
    label: 'Susu',
    bg: '#EDE9FE',
    text: '#5B21B6',
    icon: 'people-circle-outline',
  },
  SUSU_DISBURSEMENT: {
    label: 'Susu payout',
    bg: colors.status.successBg,
    text: colors.status.successText,
    icon: 'gift-outline',
  },
  VAULT_DEPOSIT: {
    label: 'To vault',
    bg: colors.gold.light,
    text: colors.gold.text,
    icon: 'arrow-forward-circle-outline',
  },
  VAULT_WITHDRAWAL: {
    label: 'From vault',
    bg: colors.gold.light,
    text: colors.gold.text,
    icon: 'arrow-back-circle-outline',
  },
  EARLY_EXIT_PENALTY: {
    label: 'Penalty',
    bg: colors.status.errorBg,
    text: colors.status.errorText,
    icon: 'alert-circle-outline',
  },
  SUSU_PENALTY: {
    label: 'Penalty',
    bg: colors.status.errorBg,
    text: colors.status.errorText,
    icon: 'alert-circle-outline',
  },
  OTHER: {
    label: 'Activity',
    bg: colors.neutral[100],
    text: colors.neutral[700],
    icon: 'ellipse-outline',
  },
};

interface Props {
  type: TransactionType;
}

export function TransactionBadge({ type }: Props) {
  const config = CONFIG[type] ?? CONFIG.OTHER;
  return (
    <View style={[styles.badge, { backgroundColor: config.bg }]} testID={`badge-${type}`}>
      <Icon name={config.icon} size={11} color={config.text} />
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
    borderRadius: radii.sm,
    gap: 4,
  },
  label: { fontSize: 11, fontWeight: '600' },
});
