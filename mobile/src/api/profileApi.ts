import { apiClient } from './client';

export interface UserProfile {
  id: string;
  email: string;
  display_name: string;
  phone: string | null;
  kyc_status: string;
  subscription_tier: string;
  account_status: string;
  email_verified_at: string | null;
  created_at: string;
}

export interface UpdateProfileRequest {
  displayName?: string;
  phone?: string;
}

export async function fetchProfile(): Promise<UserProfile> {
  const { data } = await apiClient.get<UserProfile>('/api/v1/users/me');
  return data;
}

export async function updateProfile(request: UpdateProfileRequest): Promise<UserProfile> {
  const { data } = await apiClient.patch<UserProfile>('/api/v1/users/me', request);
  return data;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export async function changePassword(request: ChangePasswordRequest): Promise<void> {
  await apiClient.post('/api/v1/users/me/change-password', {
    current_password: request.currentPassword,
    new_password: request.newPassword,
  });
}
