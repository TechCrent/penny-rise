import { apiClient } from './client';

export interface SubscriptionStatus {
  tier: 'FREE' | 'PREMIUM';
  started_at: string;
  ends_at: string | null;
  source: string;
  free_transfers_remaining: number;
}

export interface UpgradeInitiateResponse {
  authorization_url: string;
  reference: string;
}

export interface UpgradeResponse {
  tier: 'PREMIUM';
  started_at: string;
}

export interface FrozenVaultPreview {
  vault_id: string;
  vault_name: string;
  vault_type: 'STANDARD' | 'LOCKED';
}

export interface FrozenSusuGroupPreview {
  susu_group_id: string;
  susu_group_name: string;
}

export interface DowngradePreviewResponse {
  vaults_to_be_frozen: FrozenVaultPreview[];
  susu_groups_to_be_frozen: FrozenSusuGroupPreview[];
  committed: boolean;
}

export async function fetchSubscriptionStatus(): Promise<SubscriptionStatus> {
  const { data } = await apiClient.get<SubscriptionStatus>('/api/v1/me/subscription');
  return data;
}

export async function initiateUpgrade(): Promise<UpgradeInitiateResponse> {
  const { data } = await apiClient.post<UpgradeInitiateResponse>(
    '/api/v1/me/subscription/upgrade/initiate',
  );
  return data;
}

export async function confirmUpgrade(reference: string): Promise<UpgradeResponse> {
  const { data } = await apiClient.post<UpgradeResponse>('/api/v1/me/subscription/upgrade', {
    paystack_subscription_token: reference,
  });
  return data;
}

export async function fetchDowngradePreview(): Promise<DowngradePreviewResponse> {
  const { data } = await apiClient.post<DowngradePreviewResponse>(
    '/api/v1/me/subscription/downgrade',
    { confirm: false },
  );
  return data;
}

export async function commitDowngrade(): Promise<DowngradePreviewResponse> {
  const { data } = await apiClient.post<DowngradePreviewResponse>(
    '/api/v1/me/subscription/downgrade',
    { confirm: true },
  );
  return data;
}
