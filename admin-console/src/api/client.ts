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
    // v0.5-033 fix: monolith's AdminJwtAuthenticationFilter and audit-service's
    // AuditAdminJwtAuthenticationFilter both only read Authorization: Bearer
    // <token> — neither ever checked X-Admin-Token, so every existing
    // admin-console call to either has been silently unauthenticated against
    // a real backend (masked by mocked API calls in every existing test, so
    // nothing failed loudly). kyc-service's filter is explicitly named
    // PlaceholderAdminAuthFilter and still only checks X-Admin-Token, so
    // that header is kept too rather than dropped — sending both is
    // harmless and keeps every backend working without picking a side in a
    // migration that isn't this issue's to finish.
    config.headers.Authorization = `Bearer ${token}`;
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
