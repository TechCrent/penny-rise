import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../client';

export interface DepositPayload {
  amount: number;
  payment_method: 'MOMO' | 'CARD';
  mobile_number?: string;
  mobile_provider?: string;
}

export interface DepositResponse {
  transaction_reference: string;
  authorisation_url: string | null;
  paystack_reference: string;
  status: string;
}

export function useVaultDeposit(vaultId: string) {
  const queryClient = useQueryClient();
  return useMutation<DepositResponse, Error, { payload: DepositPayload; idempotencyKey: string }>({
    mutationFn: async ({ payload, idempotencyKey }) => {
      const { data } = await apiClient.post<DepositResponse>(
        `/api/v1/vaults/${vaultId}/deposits`,
        payload,
        { headers: { 'Idempotency-Key': idempotencyKey } },
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vault', vaultId] });
      queryClient.invalidateQueries({ queryKey: ['vaults'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
    },
  });
}
