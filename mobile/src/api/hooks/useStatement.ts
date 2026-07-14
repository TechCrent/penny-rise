import { useInfiniteQuery } from '@tanstack/react-query';
import { apiClient } from '../client';

export interface StatementEntry {
  entry_id: string;
  direction: 'CREDIT' | 'DEBIT';
  amount_pesewas: number;
  amount_cedis: string;
  running_balance_pesewas: number;
  running_balance_cedis: string;
  transaction_reference: string;
  transaction_type: string;
  narrative: string | null;
  created_at: string;
}

export interface StatementPage {
  entries: StatementEntry[];
  next_cursor: string | null;
  has_more: boolean;
  total_entries_on_page: number;
}

export function useStatement(vaultId: string, enabled = true) {
  return useInfiniteQuery<StatementPage>({
    queryKey: ['statement', vaultId],
    queryFn: async ({ pageParam }) => {
      const params: Record<string, string> = { limit: '20' };
      if (pageParam) params.cursor = pageParam as string;

      const { data } = await apiClient.get<StatementPage>(`/api/v1/vaults/${vaultId}/statement`, {
        params,
      });
      return data;
    },
    initialPageParam: null,
    getNextPageParam: lastPage => lastPage.next_cursor ?? undefined,
    enabled: !!vaultId && enabled,
    staleTime: 20_000,
  });
}
