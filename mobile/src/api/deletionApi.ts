import axios from 'axios';
import { apiClient } from './client';
import type { DeletionBlocker, DeletionRequestStatus } from '../screens/DeleteAccount/types';

// Proposed — no read endpoint exists for blockers yet. The only blocker
// evaluation logic in the backend (DeletionBlockerEvaluationService) has a
// single implementation that's a stub throwing UnsupportedOperationException
// (v0.2-019 scope note), so a real call here would currently fail — the
// hook treats any failure as "no blockers to show" rather than blocking the
// pre-submission screen, since this list is informational, never a gate.
export async function fetchDeletionBlockers(): Promise<DeletionBlocker[]> {
  const { data } = await apiClient.get<DeletionBlocker[]>('/api/v1/users/me/deletion-blockers');
  return data;
}

// Proposed — no GET exists for the current user's active deletion request.
// Designed to return the same shape as the real POST response below, reusing
// DeletionRequestRepository.findPendingByUserId server-side.
export async function fetchActiveDeletionRequest(): Promise<DeletionRequestStatus | null> {
  try {
    const { data } = await apiClient.get<DeletionRequestStatus>(
      '/api/v1/users/me/deletion-request',
    );
    return data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) return null;
    throw error;
  }
}

// Real, already-shipped endpoint (v0.2-019). Returns 201 with this exact
// snake_case shape; 409 if a PENDING request already exists.
export async function submitDeletionRequest(): Promise<DeletionRequestStatus> {
  const { data } = await apiClient.post<DeletionRequestStatus>('/api/v1/users/me/deletion-request');
  return data;
}

// Real, already-shipped endpoint (v0.2-019). Returns 204; 404 if none pending.
export async function cancelDeletionRequest(): Promise<void> {
  await apiClient.delete('/api/v1/users/me/deletion-request');
}
