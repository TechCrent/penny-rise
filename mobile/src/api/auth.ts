import { apiClient } from './client';

export interface SignupRequest {
  email: string;
  password: string;
  display_name: string;
  referral_code?: string;
}

export interface SignupResponse {
  id: string;
  email: string;
  display_name: string;
  message: string;
}

export async function signup(request: SignupRequest): Promise<SignupResponse> {
  const { data } = await apiClient.post<SignupResponse>('/api/v1/auth/signup', request);
  return data;
}

export interface LoginRequest {
  email: string;
  password: string;
  deviceId?: string;
  deviceLabel?: string;
}

export interface LoginResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user: {
    id: string;
    email: string;
    display_name: string;
    kyc_status: string;
  };
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/api/v1/auth/login', {
    email: request.email,
    password: request.password,
    deviceId: request.deviceId ?? 'mobile',
    deviceLabel: request.deviceLabel ?? 'Stash Mobile',
  });
  return data;
}

export async function forgotPassword(email: string): Promise<void> {
  await apiClient.post('/api/v1/auth/forgot-password', { email });
}

export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await apiClient.post('/api/v1/auth/reset-password', {
    token,
    new_password: newPassword,
  });
}

export async function resendVerification(email: string): Promise<void> {
  await apiClient.post('/api/v1/auth/resend-verification', { email });
}

export async function verifyEmail(token: string): Promise<void> {
  await apiClient.get(`/api/v1/auth/verify-email?token=${encodeURIComponent(token)}`);
}
