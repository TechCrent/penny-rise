import { apiClient } from './client';
import type { HistScope, UnifiedTransactionPage } from '../screens/TransactionHistory/types';

export interface FetchTransactionHistoryParams {
  transactionType?: string;
  fromDate?: string;
  toDate?: string;
  cursor?: string;
  limit?: number;
  scope?: HistScope;
}

export async function fetchUnifiedTransactions(
  params: FetchTransactionHistoryParams = {},
): Promise<UnifiedTransactionPage> {
  const { data } = await apiClient.get<UnifiedTransactionPage>('/api/v1/users/me/transactions', {
    params: {
      transactionType: params.transactionType,
      fromDate: params.fromDate,
      toDate: params.toDate,
      cursor: params.cursor,
      limit: params.limit,
      scope: params.scope,
    },
  });
  return data;
}
