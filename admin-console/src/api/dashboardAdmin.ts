import { adminApiClient } from './client';

export interface AdminDashboardSummary {
  pending_kyc_count: number;
  flagged_accounts_count: number;
  open_disputes_count: number;
  flagged_susu_groups_count: number;
}

export async function fetchDashboardSummary(): Promise<AdminDashboardSummary> {
  const { data } = await adminApiClient.get<AdminDashboardSummary>('/api/v1/admin/dashboard');
  return data;
}
