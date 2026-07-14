import axios from 'axios';

// All admin-console calls go through the monolith, which routes internally to
// KYC, audit, payments, etc.
export const adminApiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

adminApiClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('pennyrise_admin_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
    config.headers['X-Admin-Token'] = token;
  }
  return config;
});

// When the server returns 401 the stored JWT has expired or been revoked.
// Clear the session and redirect to login so the user sees the login page
// instead of every section showing a red error banner.
adminApiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      sessionStorage.removeItem('pennyrise_admin_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  },
);

export interface PennyRiseApiError {
  code: string;
  message: string;
  details?: Record<string, string>;
  correlation_id?: string;
}

export function extractApiError(error: unknown): PennyRiseApiError | null {
  if (axios.isAxiosError(error) && error.response?.data?.error) {
    return error.response.data.error as PennyRiseApiError;
  }
  return null;
}
