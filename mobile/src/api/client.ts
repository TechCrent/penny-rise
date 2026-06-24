import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { performTokenRefresh } from './authRefresh';
import { clearSession, getAccessToken, getRefreshToken, persistSession } from '../auth/authSession';
import { decodeKycStatusFromJwt } from '../auth/jwt';

const BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

const AUTH_PATHS_WITHOUT_BEARER = [
  '/api/v1/auth/login',
  '/api/v1/auth/signup',
  '/api/v1/auth/refresh',
  '/api/v1/auth/forgot-password',
  '/api/v1/auth/reset-password',
  '/api/v1/auth/resend-verification',
  '/api/v1/auth/verify-email',
];

function isAuthPathWithoutBearer(url: string | undefined): boolean {
  if (!url) return false;
  return AUTH_PATHS_WITHOUT_BEARER.some(path => url.includes(path));
}

apiClient.interceptors.request.use(async config => {
  if (isAuthPathWithoutBearer(config.url)) {
    return config;
  }

  const accessToken = await getAccessToken();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise;
  }

  refreshPromise = (async () => {
    const refreshToken = await getRefreshToken();
    if (!refreshToken) {
      await clearSession();
      return null;
    }

    try {
      const response = await performTokenRefresh(refreshToken);
      const kycStatus = decodeKycStatusFromJwt(response.access_token);
      await persistSession({
        accessToken: response.access_token,
        refreshToken: response.refresh_token,
        kycStatus,
      });
      return response.access_token;
    } catch {
      await clearSession();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();

  return refreshPromise;
}

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

apiClient.interceptors.response.use(
  response => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableRequestConfig | undefined;
    const status = error.response?.status;

    if (status !== 401 || !config || config._retry || isAuthPathWithoutBearer(config.url)) {
      return Promise.reject(error);
    }

    config._retry = true;
    const newAccessToken = await refreshAccessToken();

    if (!newAccessToken) {
      return Promise.reject(error);
    }

    config.headers.Authorization = `Bearer ${newAccessToken}`;
    return apiClient(config);
  },
);

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
