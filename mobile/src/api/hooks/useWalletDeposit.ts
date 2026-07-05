import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../client';
import type { DepositPayload, DepositResponse } from './useVaultDeposit';

export function useWalletDeposit() {
  const queryClient = useQueryClient();
  return useMutation<DepositResponse, Error, { payload: DepositPayload; idempotencyKey: string }>({
    mutationFn: async ({ payload, idempotencyKey }) => {
      const { data } = await apiClient.post<DepositResponse>(
        '/api/v1/users/me/deposits',
        payload,
        { headers: { 'Idempotency-Key': idempotencyKey } },
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wallet-balance'] });
      queryClient.invalidateQueries({ queryKey: ['statement'] });
    },
  });
}
