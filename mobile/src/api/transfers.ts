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
    const { data } = await apiClient.post<{
      id: string;
      transaction_reference: string;
      amount: number;
      amount_cedis: string;
      fee_amount: number;
      fee_amount_cedis: string;
      total_debited: number;
      total_debited_cedis: string;
      free_transfers_remaining: number;
      recipient_user_id: string;
      status: string;
      completed_at: string;
    }>(
      '/api/v1/transfers',
      {
        recipient_user_id: params.recipientId,
        amount: params.amountPesewas,
        narrative: params.note,
      },
      { headers: { 'Idempotency-Key': params.idempotencyKey } },
    );
    return {
      id: data.id,
      transactionReference: data.transaction_reference,
      amount: data.amount,
      amountCedis: data.amount_cedis,
      feeAmount: data.fee_amount,
      feeAmountCedis: data.fee_amount_cedis,
      totalDebited: data.total_debited,
      totalDebitedCedis: data.total_debited_cedis,
      freeTransfersRemaining: data.free_transfers_remaining,
      recipientUserId: data.recipient_user_id,
      status: data.status,
      completedAt: data.completed_at,
    };
  },

  listRecent: async (): Promise<{ transfers: RecentTransfer[] }> => {
    const { data } = await apiClient.get<{ transfers: RecentTransfer[] }>(
      '/api/v1/transfers?direction=sent&limit=5',
    );
    return data;
  },
};
