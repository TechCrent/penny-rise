import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchNotifications,
  markNotificationRead,
  markAllNotificationsRead,
} from '../../api/notificationsApi';
import type { NotificationInboxPage } from './types';

export const NOTIFICATIONS_QUERY_KEY = ['notifications'] as const;
const UNREAD_COUNT_QUERY_KEY = ['notifications', 'unread-count'] as const;

type NotificationsInfiniteData = { pages: NotificationInboxPage[]; pageParams: unknown[] };

export function useNotificationsList() {
  const queryClient = useQueryClient();

  const query = useInfiniteQuery({
    queryKey: NOTIFICATIONS_QUERY_KEY,
    queryFn: ({ pageParam }: { pageParam: string | undefined }) =>
      fetchNotifications({ cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: lastPage =>
      lastPage.has_more ? (lastPage.next_cursor ?? undefined) : undefined,
  });

  const notifications = query.data?.pages.flatMap(page => page.notifications) ?? [];
  // unread_count is per-page in the API response (same value each time, per
  // v0.5-015's design) — the most recent page's value is authoritative.
  const unreadCount = query.data?.pages.at(-1)?.unread_count ?? 0;

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onMutate: async (id: string) => {
      // Optimistic update — the AC wants the unread indicator to disappear
      // immediately on tap, not after a round trip.
      await queryClient.cancelQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
      const previous = queryClient.getQueryData<NotificationsInfiniteData>(NOTIFICATIONS_QUERY_KEY);

      queryClient.setQueryData<NotificationsInfiniteData>(NOTIFICATIONS_QUERY_KEY, old => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map(page => ({
            ...page,
            notifications: page.notifications.map(n =>
              n.id === id && n.read_at === null ? { ...n, read_at: new Date().toISOString() } : n,
            ),
            unread_count: page.notifications.some(n => n.id === id && n.read_at === null)
              ? Math.max(0, page.unread_count - 1)
              : page.unread_count,
          })),
        };
      });

      return { previous };
    },
    onError: (_err, _id, context) => {
      // A genuine network failure should roll the optimistic update back
      // rather than show a false read state; mark-read is idempotent on the
      // backend so retrying is always safe.
      if (context?.previous) queryClient.setQueryData(NOTIFICATIONS_QUERY_KEY, context.previous);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_QUERY_KEY });
    },
  });

  const markAllReadMutation = useMutation({
    mutationFn: markAllNotificationsRead,
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
      const previous = queryClient.getQueryData<NotificationsInfiniteData>(NOTIFICATIONS_QUERY_KEY);

      queryClient.setQueryData<NotificationsInfiniteData>(NOTIFICATIONS_QUERY_KEY, old => {
        if (!old) return old;
        return {
          ...old,
          pages: old.pages.map(page => ({
            ...page,
            notifications: page.notifications.map(n =>
              n.read_at ? n : { ...n, read_at: new Date().toISOString() },
            ),
            unread_count: 0,
          })),
        };
      });

      return { previous };
    },
    onError: (_err, _vars, context) => {
      if (context?.previous) queryClient.setQueryData(NOTIFICATIONS_QUERY_KEY, context.previous);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_QUERY_KEY });
    },
  });

  return {
    notifications,
    unreadCount,
    isLoading: query.isLoading,
    isError: query.isError,
    hasNextPage: query.hasNextPage,
    isFetchingNextPage: query.isFetchingNextPage,
    fetchNextPage: query.fetchNextPage,
    refetch: query.refetch,
    isRefetching: query.isRefetching,
    markRead: markReadMutation.mutate,
    markAllRead: markAllReadMutation.mutate,
  };
}

/**
 * Lighter-weight than useNotificationsList — used for the Home screen bell
 * badge, which only needs a count, not the full paginated infinite-query
 * machinery. Kept as a plain useQuery on the same endpoint (first page
 * only) so the badge doesn't pull in pagination state it never renders.
 */
export function useUnreadNotificationsCount() {
  const query = useQuery({
    queryKey: UNREAD_COUNT_QUERY_KEY,
    queryFn: () => fetchNotifications({ limit: 1 }),
    staleTime: 30_000,
  });

  return query.data?.unread_count ?? 0;
}
