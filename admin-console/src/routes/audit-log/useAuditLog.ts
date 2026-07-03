import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import {
  fetchAuditLog,
  fetchAuditLogFacets,
  type AuditLogFilterParams,
} from '../../api/auditLogAdmin';

export function useAuditLogFacets() {
  return useQuery({
    queryKey: ['auditLogFacets'],
    queryFn: fetchAuditLogFacets,
    staleTime: 5 * 60_000, // event types don't change minute-to-minute
  });
}

export function useAuditLog(filters: Omit<AuditLogFilterParams, 'cursor' | 'limit'>) {
  const query = useInfiniteQuery({
    queryKey: ['auditLog', filters],
    queryFn: ({ pageParam }: { pageParam: string | undefined }) =>
      fetchAuditLog({ ...filters, cursor: pageParam }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) =>
      lastPage.hasMore ? (lastPage.nextCursor ?? undefined) : undefined,
  });

  const entries = query.data?.pages.flatMap((page) => page.entries) ?? [];

  return {
    entries,
    isLoading: query.isLoading,
    isError: query.isError,
    hasNextPage: query.hasNextPage,
    isFetchingNextPage: query.isFetchingNextPage,
    fetchNextPage: query.fetchNextPage,
  };
}
