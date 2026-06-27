import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../client';

export interface WithdrawalPayload {
  amount: number;
  destination_momo_number: string;
  momo_provider: 'mtn' | 'vodafone' | 'airteltigo';
}

export interface WithdrawalResponse {
  transaction_reference: string;
  paystack_transfer_code: string;
  status: string;
}

export function useVaultWithdrawal(vaultId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    WithdrawalResponse,
    Error,
    { payload: WithdrawalPayload; idempotencyKey: string }
  >({
    mutationFn: async ({ payload, idempotencyKey }) => {
      const { data } = await apiClient.post<WithdrawalResponse>(
        `/api/v1/vaults/${vaultId}/withdrawals`,
        payload,
        { headers: { 'Idempotency-Key': idempotencyKey } },
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['statement'] });
      queryClient.invalidateQueries({ queryKey: ['vault', vaultId] });
      queryClient.invalidateQueries({ queryKey: ['vaults'] });
    },
  });
}
