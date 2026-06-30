import { apiClient } from './client';

export interface StatementEntry {
  id: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  running_balance: number;
  transaction_reference: string;
  narrative: string;
  transaction_type: string;
  created_at: string;
  counterparty?: string;
}

export interface StatementPage {
  entries: StatementEntry[];
  next_cursor: string | null;
  has_more: boolean;
}

export const paymentsApi = {
  getStatement: async (
    accountId: string,
    cursor?: string | null,
    limit = 20,
  ): Promise<StatementPage> => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor) params.append('cursor', cursor);
    const { data } = await apiClient.get<StatementPage>(
      `/api/v1/accounts/${accountId}/statement?${params}`,
    );
    return data;
  },
};
