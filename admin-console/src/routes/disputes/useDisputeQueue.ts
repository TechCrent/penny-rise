import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchDisputeQueue,
  assignDispute,
  type DisputeQueueParams,
  type AdminDisputeListResponse,
} from '../../api/disputesAdmin';

export function useDisputeQueue(params: DisputeQueueParams) {
  const queryClient = useQueryClient();
  const queueKey = ['adminDisputes', 'queue', params];

  const queueQuery = useQuery({
    queryKey: queueKey,
    queryFn: () => fetchDisputeQueue(params),
    placeholderData: (prev) => prev,
  });

  const assignMutation = useMutation({
    mutationFn: assignDispute,
    onSuccess: (_data, disputeId) => {
      // Patch the cached list so the row flips to IN_REVIEW immediately,
      // instead of waiting on a full refetch.
      queryClient.setQueryData<AdminDisputeListResponse>(queueKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          disputes: old.disputes.map((d) =>
            d.id === disputeId ? { ...d, status: 'IN_REVIEW' as const } : d,
          ),
        };
      });
    },
  });

  return {
    queueQuery,
    assign: assignMutation.mutate,
    assigningId: assignMutation.variables,
    isAssigning: assignMutation.isPending,
  };
}
