import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchUserDetail, suspendUser, restoreUser, forceLogoutUser } from '../../api/usersAdmin';

export function useUserDetail(userId: string) {
  const queryClient = useQueryClient();
  const detailKey = ['adminUsers', 'detail', userId];

  const detailQuery = useQuery({
    queryKey: detailKey,
    queryFn: () => fetchUserDetail(userId),
    enabled: !!userId,
  });

  const suspendMutation = useMutation({
    mutationFn: (reason: string) => suspendUser(userId, reason),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: detailKey }),
  });

  const restoreMutation = useMutation({
    mutationFn: () => restoreUser(userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: detailKey }),
  });

  const forceLogoutMutation = useMutation({
    mutationFn: () => forceLogoutUser(userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: detailKey }),
  });

  return {
    detailQuery,
    suspend: suspendMutation.mutate,
    isSuspending: suspendMutation.isPending,
    restore: restoreMutation.mutate,
    isRestoring: restoreMutation.isPending,
    forceLogout: forceLogoutMutation.mutate,
    isForcingLogout: forceLogoutMutation.isPending,
  };
}
