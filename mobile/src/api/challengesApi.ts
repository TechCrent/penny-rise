import { apiClient } from './client';
import type { Challenge } from '../screens/Challenges/types';

// GET /api/v1/challenges and GET /api/v1/challenges/{id} don't exist yet on
// the backend — only POST /{id}/join is built. See the PR notes for the
// proposed contract these calls target.
export async function fetchChallenges(): Promise<Challenge[]> {
  const { data } = await apiClient.get<Challenge[]>('/api/v1/challenges');
  return data;
}

export async function fetchChallengeDetail(id: string): Promise<Challenge> {
  const { data } = await apiClient.get<Challenge>(`/api/v1/challenges/${id}`);
  return data;
}

export interface JoinChallengeResponse {
  id: string;
  challengeId: string;
  status: string;
  targetAmount: number | null;
  progressAmount: number;
  enrolledAt: string;
}

export async function joinChallenge(challengeId: string): Promise<JoinChallengeResponse> {
  const { data } = await apiClient.post<JoinChallengeResponse>(
    `/api/v1/challenges/${challengeId}/join`,
  );
  return data;
}
