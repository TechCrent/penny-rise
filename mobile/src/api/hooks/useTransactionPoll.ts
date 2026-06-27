import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';
import type { TransactionDetail } from './useTransactionDetail';

export function useTransactionPoll(reference: string | null, enabled: boolean) {
  return useQuery<TransactionDetail>({
    queryKey: ['tx-poll', reference],
    queryFn: async () => {
      const { data } = await apiClient.get<TransactionDetail>(`/api/v1/transactions/${reference}`);
      return data;
    },
    enabled: !!reference && enabled,
    refetchInterval: query => {
      const status = query.state.data?.status;
      if (status === 'COMPLETED' || status === 'FAILED') return false;
      return 3000;
    },
    staleTime: 0,
    retry: 2,
  });
}
