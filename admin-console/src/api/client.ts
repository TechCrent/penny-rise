import axios from 'axios';

const BASE_URL =
  import.meta.env.VITE_KYC_API_URL ?? import.meta.env.VITE_API_URL ?? 'http://localhost:8082';

export const adminApiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

adminApiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('stash_admin_token');
  if (token) {
    config.headers['X-Admin-Token'] = token;
  }
  return config;
});

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
