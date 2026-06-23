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
