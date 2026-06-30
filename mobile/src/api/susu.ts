import { apiClient } from './client';
import type {
  SusuGroupListResponse,
  SusuGroupDetailResponse,
  SusuActivationResponse,
  SusuContributionResponse,
} from '../types/susu';

export const susuApi = {
  listGroups: async (includeInactive = false): Promise<SusuGroupListResponse[]> => {
    const { data } = await apiClient.get<SusuGroupListResponse[]>(
      `/api/v1/susu/groups${includeInactive ? '?include_inactive=true' : ''}`
    );
    return data;
  },

  getGroupDetail: async (groupId: string): Promise<SusuGroupDetailResponse> => {
    const { data } = await apiClient.get<SusuGroupDetailResponse>(
      `/api/v1/susu/groups/${groupId}`
    );
    return data;
  },

  activateGroup: async (
    groupId: string,
    idempotencyKey: string,
  ): Promise<SusuActivationResponse> => {
    const { data } = await apiClient.post<SusuActivationResponse>(
      `/api/v1/susu/groups/${groupId}/activate`,
      {},
      { headers: { 'Idempotency-Key': idempotencyKey } }
    );
    return data;
  },

  payContribution: async (
    roundId: string,
    idempotencyKey: string,
  ): Promise<SusuContributionResponse> => {
    const { data } = await apiClient.post<SusuContributionResponse>(
      `/api/v1/susu/contributions/${roundId}`,
      {},
      { headers: { 'Idempotency-Key': idempotencyKey } }
    );
    return data;
  },
};
