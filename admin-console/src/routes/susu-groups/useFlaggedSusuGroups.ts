import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchFlaggedSusuGroups,
  fetchAllSusuGroups,
  clearSusuGroupFlag,
} from '../../api/susuGroupsAdmin';

export function useFlaggedSusuGroups(page: number, enabled = true) {
  return useQuery({
    queryKey: ['flaggedSusuGroups', page],
    queryFn: () => fetchFlaggedSusuGroups(page),
    placeholderData: (prev) => prev,
    enabled,
  });
}

export function useAllSusuGroups(page: number, enabled = true) {
  return useQuery({
    queryKey: ['allSusuGroups', page],
    queryFn: () => fetchAllSusuGroups(page),
    placeholderData: (prev) => prev,
    enabled,
  });
}

export function useClearSusuGroupFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => clearSusuGroupFlag(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flaggedSusuGroups'] });
      queryClient.invalidateQueries({ queryKey: ['allSusuGroups'] });
    },
  });
}
