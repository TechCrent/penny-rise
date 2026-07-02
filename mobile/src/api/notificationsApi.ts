import { apiClient } from './client';
import type { NotificationInboxPage } from '../features/notifications/types';

export interface FetchNotificationsParams {
  unreadOnly?: boolean;
  cursor?: string;
  limit?: number;
}

export async function fetchNotifications(
  params: FetchNotificationsParams = {},
): Promise<NotificationInboxPage> {
  const { data } = await apiClient.get<NotificationInboxPage>('/api/v1/notifications', {
    params: {
      unread_only: params.unreadOnly ?? undefined,
      cursor: params.cursor,
      limit: params.limit,
    },
  });
  return data;
}

export async function markNotificationRead(id: string): Promise<void> {
  await apiClient.patch(`/api/v1/notifications/${id}/read`);
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.patch('/api/v1/notifications/read-all');
}
