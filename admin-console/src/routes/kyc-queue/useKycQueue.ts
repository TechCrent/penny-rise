import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchQueue,
  fetchSubmissionDetail,
  approveSubmission,
  rejectSubmission,
  bulkApproveSubmissions,
} from '../../api/kycAdmin';

export type ModalState =
  | { kind: 'closed' }
  | { kind: 'detail'; submissionId: string }
  | { kind: 'reject'; submissionId: string; reason: string };

export function useKycQueue() {
  const queryClient = useQueryClient();
  const [modalState, setModalState] = useState<ModalState>({ kind: 'closed' });
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const submissionId = modalState.kind !== 'closed' ? modalState.submissionId : '';

  const queueQuery = useQuery({
    queryKey: ['kycQueue'],
    queryFn: () => fetchQueue(),
  });

  const detailQuery = useQuery({
    queryKey: ['kycSubmissionDetail', submissionId],
    queryFn: () => fetchSubmissionDetail(submissionId),
    enabled: modalState.kind !== 'closed',
  });

  const approveMutation = useMutation({
    mutationFn: (id: string) => approveSubmission(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kycQueue'] });
      setModalState({ kind: 'closed' });
      setSuccessMessage('Submission approved successfully.');
      setTimeout(() => setSuccessMessage(null), 4000);
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => rejectSubmission(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kycQueue'] });
      setModalState({ kind: 'closed' });
      setSuccessMessage('Submission rejected.');
      setTimeout(() => setSuccessMessage(null), 4000);
    },
  });

  const bulkApproveMutation = useMutation({
    mutationFn: (ids: string[]) => bulkApproveSubmissions(ids),
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: ['kycQueue'] });
      setSelectedIds(new Set());
      const failures = response.results.filter((r) => !r.success);
      setSuccessMessage(
        failures.length === 0
          ? `${response.results.length} submission(s) approved successfully.`
          : `${response.results.length - failures.length} approved, ${failures.length} failed.`,
      );
      setTimeout(() => setSuccessMessage(null), 6000);
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

  const toggleSelectAll = () => {
    const items = queueQuery.data?.items ?? [];
    setSelectedIds((prev) =>
      items.length > 0 && items.every((i) => prev.has(i.submission_id))
        ? new Set()
        : new Set(items.map((i) => i.submission_id)),
    );
  };

  return {
    queueQuery,
    detailQuery,
    modalState,
    setModalState,
    successMessage,
    approveMutation,
    rejectMutation,
    selectedIds,
    toggleSelect,
    toggleSelectAll,
    bulkApproveMutation,
  };
}
