import { Share } from 'react-native';
import { apiClient } from './client';
import type {
  SusuGroupListResponse,
  SusuGroupDetailResponse,
  SusuActivationResponse,
  SusuContributionResponse,
} from '../types/susu';

type WalletBalanceResponse = {
  account_id: string;
  balance_pesewas: number;
  balance_cedis: string;
};

type CreateGroupResponse = { id: string; join_code: string; status: string };

type JoinGroupResponse = {
  group: {
    id: string;
    name: string;
    contribution_amount_cedis: string;
    frequency: string;
    target_member_count: number;
    current_member_count: number;
    status: string;
  };
  membership: { id: string; status: string };
};

export const susuApi = {
  listGroups: async (includeInactive = false): Promise<SusuGroupListResponse[]> => {
    const { data } = await apiClient.get<SusuGroupListResponse[]>(
      `/api/v1/susu/groups${includeInactive ? '?include_inactive=true' : ''}`,
    );
    return data;
  },

  getGroupDetail: async (groupId: string): Promise<SusuGroupDetailResponse> => {
    const { data } = await apiClient.get<SusuGroupDetailResponse>(`/api/v1/susu/groups/${groupId}`);
    return data;
  },

  activateGroup: async (
    groupId: string,
    idempotencyKey: string,
  ): Promise<SusuActivationResponse> => {
    const { data } = await apiClient.post<SusuActivationResponse>(
      `/api/v1/susu/groups/${groupId}/activate`,
      {},
      { headers: { 'Idempotency-Key': idempotencyKey } },
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
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return data;
  },

  createGroup: async (
    groupData: {
      name: string;
      contribution_amount: number;
      frequency: string;
      target_member_count: number;
    },
    idempotencyKey: string,
  ): Promise<CreateGroupResponse> => {
    const { data } = await apiClient.post<CreateGroupResponse>('/api/v1/susu/groups', groupData, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
    return data;
  },

  joinGroup: async (joinCode: string, idempotencyKey: string): Promise<JoinGroupResponse> => {
    const { data } = await apiClient.post<JoinGroupResponse>(
      '/api/v1/susu/groups/join',
      { join_code: joinCode },
      { headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return data;
  },
};

export const walletApi = {
  getBalance: async (): Promise<WalletBalanceResponse> => {
    const { data } = await apiClient.get<WalletBalanceResponse>('/api/v1/users/me/wallet-balance');
    return data;
  },
};

export async function shareJoinCode(code: string, groupName: string) {
  await Share.share({
    message:
      `Join my PennyRise susu group "${groupName}"! ` +
      `Use code ${code} on the PennyRise app to join. ` +
      `Download PennyRise at pennyrise.app`,
    title: `Join ${groupName} on PennyRise`,
  });
}
