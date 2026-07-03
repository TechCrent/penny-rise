import { adminApiClient } from './client';

export interface AdminUserListItem {
  id: string;
  displayName: string;
  email: string;
  phone: string;
  kycStatus: string;
  accountStatus: 'ACTIVE' | 'SUSPENDED' | 'CLOSED';
  subscriptionTier: 'FREE' | 'PREMIUM';
  createdAt: string;
  maskedGhanaCard: string | null;
}

export interface AdminUserListResponse {
  items: AdminUserListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminVaultSummary {
  id: string;
  name: string;
  vaultType: 'STANDARD' | 'LOCKED';
  status: string;
  balancePesewas: number;
}

export interface AdminSusuMembershipSummary {
  susuGroupId: string;
  susuGroupName: string;
  rotationPosition: number;
  status: string;
}

export interface AdminTransactionSummary {
  reference: string;
  type: string;
  amountPesewas: number;
  status: string;
  occurredAt: string;
}

// challenge/api document evidence — proposed extension. AdminKycSubmissionSummary
// has no document field at all today (verified against
// AdminKycSubmissionSummary.java) — this issue wants signed URLs for review
// purposes. `documents` is optional/absent until the backend adds it.
export interface KycDocument {
  documentType: 'FRONT_OF_CARD' | 'BACK_OF_CARD' | 'SELFIE';
  signedUrl: string;
  expiresAt: string;
}

export interface AdminKycSubmissionSummary {
  submissionId: string;
  status: string;
  reviewPath: string;
  decision: string | null;
  decisionReason: string | null;
  submittedAt: string;
  decidedAt: string | null;
  documents?: KycDocument[];
}

// Proposed — no sessions endpoint exists anywhere in the backend today
// (verified: no GET .../sessions route under com.stash.admin). `sessions`
// is optional/absent on AdminUserDetailResponse until it's built.
export interface AdminSessionSummary {
  id: string;
  deviceLabel: string | null;
  ipAddress: string;
  lastUsedAt: string;
  createdAt: string;
}

export interface AdminUserDetailResponse {
  user: AdminUserListItem;
  vaults: AdminVaultSummary[];
  activeSusuMemberships: AdminSusuMembershipSummary[];
  recentTransactions: AdminTransactionSummary[];
  kycSubmissionHistory: AdminKycSubmissionSummary[];
  sessions?: AdminSessionSummary[];
}

export interface UserSearchParams {
  search?: string;
  kycStatus?: string;
  accountStatus?: string;
  page?: number;
  size?: number;
}

export async function searchUsers(params: UserSearchParams): Promise<AdminUserListResponse> {
  const { data } = await adminApiClient.get<AdminUserListResponse>('/api/v1/admin/users', {
    params: {
      search: params.search || undefined,
      kycStatus: params.kycStatus || undefined,
      accountStatus: params.accountStatus || undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return data;
}

export async function fetchUserDetail(id: string): Promise<AdminUserDetailResponse> {
  const { data } = await adminApiClient.get<AdminUserDetailResponse>(`/api/v1/admin/users/${id}`);
  return data;
}

export async function suspendUser(id: string, reason: string): Promise<void> {
  await adminApiClient.post(`/api/v1/admin/users/${id}/suspend`, { reason });
}

export async function restoreUser(id: string): Promise<void> {
  await adminApiClient.post(`/api/v1/admin/users/${id}/restore`);
}

export async function forceLogoutUser(id: string): Promise<void> {
  await adminApiClient.post(`/api/v1/admin/users/${id}/force-logout`);
}
