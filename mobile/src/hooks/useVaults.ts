import { useQuery } from '@tanstack/react-query';
import { listVaults, type VaultListResponse } from '../api/vaults';

export function useVaults() {
  return useQuery<VaultListResponse>({
    queryKey: ['vaults'],
    queryFn: listVaults,
    staleTime: 30_000,
  });
}
