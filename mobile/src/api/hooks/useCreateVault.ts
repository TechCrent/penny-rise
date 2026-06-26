import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../client';

export interface CreateVaultPayload {
  name: string;
  vault_type: 'STANDARD' | 'LOCKED';
  unlock_at?: string | null;
  unlock_amount?: number | null;
  unlock_condition_logic?: 'AND' | 'OR' | null;
}

export interface CreateVaultResponse {
  id: string;
  name: string;
  vault_type: 'STANDARD' | 'LOCKED';
  status: string;
  ledger_account_id: string;
  unlock_at: string | null;
  unlock_amount: number | null;
  unlock_condition_logic: string | null;
  created_at: string;
}

export function useCreateVault() {
  const queryClient = useQueryClient();

  return useMutation<CreateVaultResponse, Error, { payload: CreateVaultPayload; idempotencyKey: string }>({
    mutationFn: async ({ payload, idempotencyKey }) => {
      const { data } = await apiClient.post<CreateVaultResponse>(
        '/api/v1/vaults',
        payload,
        { headers: { 'Idempotency-Key': idempotencyKey } },
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vaults'] });
    },
  });
}
