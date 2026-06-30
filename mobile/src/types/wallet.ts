export type TransactionDirection = 'DEBIT' | 'CREDIT';

export type TransactionType =
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'PEER_TRANSFER'
  | 'PEER_TRANSFER_FEE'
  | 'SUSU_CONTRIBUTION'
  | 'SUSU_DISBURSEMENT'
  | 'VAULT_DEPOSIT'
  | 'VAULT_WITHDRAWAL'
  | 'EARLY_EXIT_PENALTY'
  | 'SUSU_PENALTY'
  | 'OTHER';

export interface WalletActivity {
  id: string;
  direction: TransactionDirection;
  amount: number;
  amountCedis: string;
  runningBalance: number;
  runningBalanceCedis: string;
  reference: string;
  narrative: string;
  transactionType: TransactionType;
  createdAt: string;
  counterparty?: string;
}
