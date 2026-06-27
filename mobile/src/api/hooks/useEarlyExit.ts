import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../client';

export type EarlyExitReason = 'SCHOOL_FEES_EMERGENCY' | 'MEDICAL' | 'FAMILY' | 'OTHER';

export interface EarlyExitResponse {
  id: string;
  vault_id: string;
  reason: EarlyExitReason;
  balance_at_request_pesewas: number;
  balance_at_request_cedis: string;
  penalty_amount_pesewas: number;
  penalty_amount_cedis: string;
  release_amount_pesewas: number;
  release_amount_cedis: string;
  scheduled_release_at: string;
  status: string;
}

export function useRequestEarlyExit(vaultId: string) {
  const queryClient = useQueryClient();
  return useMutation<EarlyExitResponse, Error, { reason: EarlyExitReason }>({
    mutationFn: async ({ reason }) => {
      const { data } = await apiClient.post<EarlyExitResponse>(
        `/api/v1/vaults/${vaultId}/early-exit`,
        { reason },
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vaults'] });
      queryClient.invalidateQueries({ queryKey: ['vault', vaultId] });
    },
  });
}

export function useCancelEarlyExit(vaultId: string) {
  const queryClient = useQueryClient();
  return useMutation<void, Error, void>({
    mutationFn: async () => {
      await apiClient.delete(`/api/v1/vaults/${vaultId}/early-exit`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vaults'] });
      queryClient.invalidateQueries({ queryKey: ['vault', vaultId] });
    },
  });
}
