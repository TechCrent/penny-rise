import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchChallenges, fetchChallengeDetail, joinChallenge } from '../../api/challengesApi';

export const CHALLENGES_QUERY_KEY = ['challenges'] as const;
export const CHALLENGE_DETAIL_QUERY_KEY = (id: string) => ['challenges', id] as const;

export function useChallenges() {
  return useQuery({
    queryKey: CHALLENGES_QUERY_KEY,
    queryFn: fetchChallenges,
  });
}

export function useChallengeDetail(id: string) {
  return useQuery({
    queryKey: CHALLENGE_DETAIL_QUERY_KEY(id),
    queryFn: () => fetchChallengeDetail(id),
    enabled: !!id,
  });
}

export function useJoinChallenge() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: joinChallenge,
    onSuccess: (_response, challengeId) => {
      // Rather than hand-patching the cache shape (error-prone given the
      // nested enrollment object), invalidate and let both the list and
      // detail screens refetch. AC requires "challenge moves to Active
      // section; progress bar starts at 0" — a fast refetch achieves this
      // without the risk of a stale/incorrect optimistic patch.
      queryClient.invalidateQueries({ queryKey: CHALLENGES_QUERY_KEY });
      queryClient.invalidateQueries({ queryKey: CHALLENGE_DETAIL_QUERY_KEY(challengeId) });
    },
  });
}
