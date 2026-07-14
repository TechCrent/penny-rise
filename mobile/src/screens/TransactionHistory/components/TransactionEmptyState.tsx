import React from 'react';
import { EmptyState } from '../../../components/ui';
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
    subtitle: 'Send money to another PennyRise user to see transfers here.',
  },
  SUSU: {
    title: 'No susu activity yet',
    subtitle: 'Join a susu group and make a contribution to see it here.',
  },
};

export function TransactionEmptyState({ activeTab }: { activeTab: FilterTab }) {
  const { title, subtitle } = EMPTY_COPY[activeTab];
  return (
    <EmptyState icon="receipt-outline" title={title} message={subtitle} testID="empty-state" />
  );
}
