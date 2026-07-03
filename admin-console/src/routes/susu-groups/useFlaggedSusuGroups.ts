import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchFlaggedSusuGroups, clearSusuGroupFlag } from '../../api/susuGroupsAdmin';

export function useFlaggedSusuGroups(page: number) {
  return useQuery({
    queryKey: ['flaggedSusuGroups', page],
    queryFn: () => fetchFlaggedSusuGroups(page),
    placeholderData: (prev) => prev,
  });
}

export function useClearSusuGroupFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => clearSusuGroupFlag(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['flaggedSusuGroups'] });
    },
  });
}
