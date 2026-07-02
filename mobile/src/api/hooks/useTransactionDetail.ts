import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../client';

export interface TransactionEntry {
  direction: string;
  amount_pesewas: number;
  amount_cedis: string;
  account_type: string;
  account_id: string;
  narrative: string | null;
}

export interface TransactionDetail {
  reference: string;
  transaction_type: string;
  status: string;
  gross_amount_pesewas: number;
  gross_amount_cedis: string;
  fee_amount_pesewas: number;
  fee_amount_cedis: string;
  net_amount_pesewas: number;
  net_amount_cedis: string;
  initiating_user_id: string;
  counterparty_user_id: string | null;
  external_provider: string | null;
  external_reference: string | null;
  narrative: string | null;
  created_at: string;
  completed_at: string | null;
  entries: TransactionEntry[];
}

export function useTransactionDetail(reference: string | null) {
  return useQuery<TransactionDetail>({
    queryKey: ['transaction', reference],
    queryFn: async () => {
      const { data } = await apiClient.get<TransactionDetail>(`/api/v1/transactions/${reference}`);
      return data;
    },
    enabled: !!reference,
    staleTime: 60_000,
  });
}
