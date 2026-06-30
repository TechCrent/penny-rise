import { apiClient } from './client';

export type RecipientResult = {
  id: string;
  displayName: string;
  email: string;
};

export type TransferQuota = {
  freeTransfersUsed: number;
  freeTransfersRemaining: number;
  freeQuotaLimit: number;
  nextTransferIsFree: boolean;
  feeIfTransferNowPesewas: number;
  feeIfTransferNowCedis: string;
};

export type TransferResult = {
  id: string;
  transactionReference: string;
  amount: number;
  amountCedis: string;
  feeAmount: number;
  feeAmountCedis: string;
  totalDebited: number;
  totalDebitedCedis: string;
  freeTransfersRemaining: number;
  recipientUserId: string;
  status: string;
  completedAt: string;
};

export type RecentTransfer = {
  id: string;
  direction: 'SENT' | 'RECEIVED';
  counterparty_user_id: string;
  counterparty_display_name: string;
  amount: number;
  created_at: string;
};

export const transferApi = {
  searchRecipients: async (q: string): Promise<RecipientResult[]> => {
    const { data } = await apiClient.get<RecipientResult[]>('/api/v1/users/search', {
      params: { q },
    });
    return data;
  },

  getQuota: async (): Promise<TransferQuota> => {
    const { data } = await apiClient.get<TransferQuota>('/api/v1/users/me/transfer-quota');
    return data;
  },

  send: async (params: {
    recipientId: string;
    amountPesewas: number;
    note?: string;
    idempotencyKey: string;
  }): Promise<TransferResult> => {
    const { data } = await apiClient.post<TransferResult>('/api/v1/transfers', params);
    return data;
  },

  listRecent: async (): Promise<{ transfers: RecentTransfer[] }> => {
    const { data } = await apiClient.get<{ transfers: RecentTransfer[] }>(
      '/api/v1/transfers?direction=sent&limit=5',
    );
    return data;
  },
};
