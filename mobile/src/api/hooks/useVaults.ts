import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';

export interface VaultListItem {
  id: string;
  name: string;
  vault_type: 'STANDARD' | 'LOCKED';
  status: 'ACTIVE' | 'CLOSED' | 'LOCKED' | 'EARLY_EXIT_PENDING' | 'FROZEN';
  ledger_account_id: string;
  balance_pesewas: number | null;
  balance_cedis: string | null;
  unlock_at: string | null;
  unlock_amount: number | null;
  unlock_condition_logic: string | null;
  early_exit_in_progress: boolean;
  created_at: string;
}

export interface VaultListResponse {
  vaults: VaultListItem[];
  total_count: number;
  balance_unavailable_count: number;
}

export function useVaults() {
  return useQuery<VaultListResponse>({
    queryKey: ['vaults'],
    queryFn: async () => {
      const { data } = await apiClient.get<VaultListResponse>('/api/v1/vaults');
      return data;
    },
    staleTime: 30_000,
  });
}
