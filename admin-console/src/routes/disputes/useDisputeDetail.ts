import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchDisputeDetail, resolveDispute, closeDisputeNoAction } from '../../api/disputesAdmin';

export function useDisputeDetail(disputeId: string) {
  const queryClient = useQueryClient();
  const detailKey = ['adminDisputes', 'detail', disputeId];

  const detailQuery = useQuery({
    queryKey: detailKey,
    queryFn: () => fetchDisputeDetail(disputeId),
    enabled: !!disputeId,
  });

  const resolveMutation = useMutation({
    mutationFn: (notes: string) => resolveDispute(disputeId, notes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: detailKey });
      queryClient.invalidateQueries({ queryKey: ['adminDisputes', 'queue'] });
    },
  });

  const closeMutation = useMutation({
    mutationFn: (reason: string) => closeDisputeNoAction(disputeId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: detailKey });
      queryClient.invalidateQueries({ queryKey: ['adminDisputes', 'queue'] });
    },
  });

  return {
    detailQuery,
    resolve: resolveMutation.mutate,
    isResolving: resolveMutation.isPending,
    closeNoAction: closeMutation.mutate,
    isClosing: closeMutation.isPending,
  };
}
