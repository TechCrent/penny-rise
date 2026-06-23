import axios from 'axios';

const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

/**
 * Maps a server error response to a structured error object.
 * All Stash API errors follow the §6.3 envelope:
 * { error: { code, message, details, correlation_id } }
 */
export interface StashApiError {
  code: string;
  message: string;
  details?: Record<string, string>;
  correlation_id?: string;
}

export function extractApiError(error: unknown): StashApiError | null {
  if (axios.isAxiosError(error) && error.response?.data?.error) {
    return error.response.data.error as StashApiError;
  }
  return null;
}
