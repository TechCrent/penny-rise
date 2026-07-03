import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchDeletionBlockers,
  fetchActiveDeletionRequest,
  submitDeletionRequest,
  cancelDeletionRequest,
} from '../../api/deletionApi';
import type { DeletionScreenState } from './types';

const ACTIVE_REQUEST_KEY = ['deletion-request'] as const;
const BLOCKERS_KEY = ['deletion-blockers'] as const;

export function useDeletionRequest() {
  const queryClient = useQueryClient();

  const activeRequestQuery = useQuery({
    queryKey: ACTIVE_REQUEST_KEY,
    queryFn: fetchActiveDeletionRequest,
  });

  // Only fetch blockers once we know there's no active request — no point
  // showing blocker preview copy for a screen that's about to render the
  // cool-off state instead. retry: false + treated as "no blockers" on
  // failure, since the backing evaluation service is a stub today (see
  // api/deletionApi.ts) and this list is informational, never a gate.
  const blockersQuery = useQuery({
    queryKey: BLOCKERS_KEY,
    queryFn: fetchDeletionBlockers,
    enabled: activeRequestQuery.isSuccess && activeRequestQuery.data === null,
    retry: false,
  });

  const submitMutation = useMutation({
    mutationFn: submitDeletionRequest,
    onSuccess: result => {
      queryClient.setQueryData(ACTIVE_REQUEST_KEY, result);
    },
  });

  const cancelMutation = useMutation({
    mutationFn: cancelDeletionRequest,
    onSuccess: () => {
      queryClient.setQueryData(ACTIVE_REQUEST_KEY, null);
    },
  });

  let screenState: DeletionScreenState = 'LOADING';
  if (activeRequestQuery.isSuccess) {
    screenState = activeRequestQuery.data ? 'COOL_OFF' : 'PRE_SUBMISSION';
  }

  return {
    screenState,
    activeRequest: activeRequestQuery.data ?? null,
    blockers: blockersQuery.data ?? [],
    isLoadingBlockers: blockersQuery.isLoading,
    submit: submitMutation.mutate,
    isSubmitting: submitMutation.isPending,
    submitError: submitMutation.error,
    cancel: cancelMutation.mutate,
    isCancelling: cancelMutation.isPending,
    cancelError: cancelMutation.error,
  };
}
