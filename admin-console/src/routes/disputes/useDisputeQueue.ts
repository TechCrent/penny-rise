import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchDisputeQueue,
  assignDispute,
  bulkResolveDisputes,
  type DisputeQueueParams,
  type AdminDisputeListResponse,
} from '../../api/disputesAdmin';

export function useDisputeQueue(params: DisputeQueueParams) {
  const queryClient = useQueryClient();
  const queueKey = ['adminDisputes', 'queue', params];
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

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

  const bulkResolveMutation = useMutation({
    mutationFn: ({ ids, notes }: { ids: string[]; notes: string }) =>
      bulkResolveDisputes(ids, notes),
    onSuccess: () => {
      setSelectedIds(new Set());
      queryClient.invalidateQueries({ queryKey: ['adminDisputes'] });
    },
  });

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAllResolvable = () => {
    const resolvable = (queueQuery.data?.disputes ?? []).filter((d) => d.status === 'IN_REVIEW');
    setSelectedIds((prev) =>
      resolvable.length > 0 && resolvable.every((d) => prev.has(d.id))
        ? new Set()
        : new Set(resolvable.map((d) => d.id)),
    );
  };

  return {
    queueQuery,
    assign: assignMutation.mutate,
    assigningId: assignMutation.variables,
    isAssigning: assignMutation.isPending,
    selectedIds,
    toggleSelect,
    toggleSelectAllResolvable,
    bulkResolveMutation,
  };
}
