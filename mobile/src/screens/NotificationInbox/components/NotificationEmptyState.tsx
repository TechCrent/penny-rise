import React from 'react';
import { EmptyState } from '../../../components/ui';

export function NotificationEmptyState() {
  return (
    <EmptyState
      icon="notifications-outline"
      title="You're all caught up"
      message="Notifications about deposits, transfers, and challenges will show up here."
      testID="empty-state"
    />
  );
}
