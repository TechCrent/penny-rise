import { useInfiniteQuery } from '@tanstack/react-query';
import { fetchUnifiedTransactions } from '../../api/transactionHistoryApi';
import { FILTER_TO_QUERY_PARAM } from './types';
import type { FilterTab, UnifiedTransactionItem } from './types';

export const TRANSACTIONS_QUERY_KEY = (tab: FilterTab) => ['transactions', 'history', tab] as const;

export function useTransactionHistory(activeTab: FilterTab) {
  const transactionType = FILTER_TO_QUERY_PARAM[activeTab];

  const query = useInfiniteQuery({
    queryKey: TRANSACTIONS_QUERY_KEY(activeTab),
    queryFn: ({ pageParam }: { pageParam: string | undefined }) =>
      fetchUnifiedTransactions({ transactionType, cursor: pageParam, limit: 20 }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: lastPage =>
      lastPage.hasMore ? (lastPage.nextCursor ?? undefined) : undefined,
  });

  const transactions: UnifiedTransactionItem[] =
    query.data?.pages.flatMap(page => page.transactions) ?? [];

  return {
    transactions,
    isLoading: query.isLoading,
    isError: query.isError,
    hasNextPage: query.hasNextPage,
    isFetchingNextPage: query.isFetchingNextPage,
    fetchNextPage: query.fetchNextPage,
    isRefetching: query.isRefetching,
    refetch: query.refetch,
  };
}
