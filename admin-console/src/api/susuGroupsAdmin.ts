import { adminApiClient } from './client';

// Field names verified directly against the real backend DTOs
// (FlaggedSusuGroupListItem, AdminSusuGroupDetailResponse — no
// @JsonProperty annotations exist there, matching usersAdmin.ts's own
// camelCase convention, not snake_case.
export interface FlaggedSusuGroupListItem {
  id: string;
  name: string;
  organiserUserId: string;
  flaggedAt: string;
  lastShortfallRoundNumber: number | null;
  lastShortfallMemberUserId: string | null;
  lastShortfallAt: string | null;
  potBalancePesewas: number;
}

export interface FlaggedSusuGroupListResponse {
  items: FlaggedSusuGroupListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SusuGroupMemberDetail {
  userId: string;
  displayName: string;
  rotationPosition: number | null;
  status: string;
  joinedAt: string;
}

export interface SusuGroupContributionDetail {
  memberUserId: string;
  status: string;
  expectedAmount: number;
  collectedAmount: number | null;
  penaltyAmount: number;
  isLate: boolean;
}

export interface AdminSusuGroupDetailResponse {
  id: string;
  name: string;
  status: string;
  organiserUserId: string;
  flaggedForReview: boolean;
  flaggedAt: string | null;
  currentRoundNumber: number | null;
  members: SusuGroupMemberDetail[];
  currentRoundContributions: SusuGroupContributionDetail[];
  potBalancePesewas: number;
}

export async function fetchFlaggedSusuGroups(
  page = 0,
  size = 20,
): Promise<FlaggedSusuGroupListResponse> {
  const { data } = await adminApiClient.get<FlaggedSusuGroupListResponse>(
    '/api/v1/admin/susu-groups',
    { params: { flagged: true, page, size } },
  );
  return data;
}

export async function fetchSusuGroupDetail(id: string): Promise<AdminSusuGroupDetailResponse> {
  const { data } = await adminApiClient.get<AdminSusuGroupDetailResponse>(
    `/api/v1/admin/susu-groups/${id}`,
  );
  return data;
}

export async function clearSusuGroupFlag(id: string): Promise<void> {
  await adminApiClient.post(`/api/v1/admin/susu-groups/${id}/clear-flag`);
}
