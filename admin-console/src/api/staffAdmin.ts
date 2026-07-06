import { adminApiClient } from './client';

export type AdminAccountType = 'SUPER' | 'VICE_SUPER' | 'TAB';

export interface AdminStaffSummary {
  id: string;
  email: string;
  full_name: string;
  account_type: AdminAccountType;
  role_name: string | null;
  is_active: boolean;
  deactivated_at: string | null;
  created_at: string;
}

export interface AdminStaffListResponse {
  items: AdminStaffSummary[];
  page: number;
  size: number;
  total_elements: number;
  total_pages: number;
}

export interface CreateAdminStaffRequest {
  email: string;
  password: string;
  fullName: string;
  accountType: AdminAccountType;
  roleName?: string;
}

export async function fetchStaffList(page: number, size = 20): Promise<AdminStaffListResponse> {
  const { data } = await adminApiClient.get<AdminStaffListResponse>('/api/v1/admin/staff', {
    params: { page, size },
  });
  return data;
}

export async function createStaffAccount(
  request: CreateAdminStaffRequest,
): Promise<AdminStaffSummary> {
  const { data } = await adminApiClient.post<AdminStaffSummary>('/api/v1/admin/staff', request);
  return data;
}

export async function deactivateStaffAccount(id: string): Promise<void> {
  await adminApiClient.post(`/api/v1/admin/staff/${id}/deactivate`);
}
