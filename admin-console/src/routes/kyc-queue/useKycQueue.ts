import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchQueue,
  fetchSubmissionDetail,
  approveSubmission,
  rejectSubmission,
} from '../../api/kycAdmin';

export type ModalState =
  | { kind: 'closed' }
  | { kind: 'detail'; submissionId: string }
  | { kind: 'reject'; submissionId: string; reason: string };

export function useKycQueue() {
  const queryClient = useQueryClient();
  const [modalState, setModalState] = useState<ModalState>({ kind: 'closed' });
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

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

  return {
    queueQuery,
    detailQuery,
    modalState,
    setModalState,
    successMessage,
    approveMutation,
    rejectMutation,
  };
}
