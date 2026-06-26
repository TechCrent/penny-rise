import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';
import type { VaultListItem } from './useVaults';

export function useVaultDetail(vaultId: string) {
  return useQuery<VaultListItem>({
    queryKey: ['vault', vaultId],
    queryFn: async () => {
      const { data } = await apiClient.get<{ vaults: VaultListItem[] }>('/api/v1/vaults');
      const found = data.vaults.find(v => v.id === vaultId);
      if (!found) throw new Error('Vault not found');
      return found;
    },
    staleTime: 20_000,
  });
}
