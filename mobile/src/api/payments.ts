import { apiClient } from './client';

// Mirrors StatementEntryDto (payments-service) verbatim — this used to be a
// hand-guessed shape (id/amount/running_balance) that never matched what the
// backend actually sends, on top of hitting a route that didn't exist on the
// monolith at all. See WalletBalanceController#getWalletStatement.
export interface StatementEntry {
  entry_id: string;
  direction: 'DEBIT' | 'CREDIT';
  amount_pesewas: number;
  amount_cedis: string;
  running_balance_pesewas: number;
  running_balance_cedis: string;
  transaction_reference: string;
  transaction_type: string;
  narrative: string | null;
  created_at: string;
}

export interface StatementPage {
  entries: StatementEntry[];
  next_cursor: string | null;
  has_more: boolean;
  total_entries_on_page: number;
}

export const paymentsApi = {
  // Self-scoped to the caller's own wallet — resolved server-side, no
  // accountId param (see WalletBalanceController#getWalletStatement).
  getStatement: async (cursor?: string | null, limit = 20): Promise<StatementPage> => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (cursor) params.append('cursor', cursor);
    const { data } = await apiClient.get<StatementPage>(
      `/api/v1/users/me/wallet-statement?${params}`,
    );
    return data;
  },
};
