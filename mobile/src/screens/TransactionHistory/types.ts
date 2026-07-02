// Mirrors GET /api/v1/users/me/transactions's response shape from v0.5-020
// (monolith TransactionHistoryController / UnifiedTransactionHistoryResponse).
// Unlike most of this app's API-shaped types, these fields are genuinely
// camelCase over the wire — the monolith DTO has no @JsonProperty overrides
// and no snake_case Jackson naming strategy is configured, confirmed against
// TransactionHistoryController.java / UnifiedTransactionItem.java. This is
// unlike GET /api/v1/transactions/{ref} (Payments Service, see
// api/hooks/useTransactionDetail.ts), which is snake_case — the two services
// don't agree on a casing convention.

export type TransactionDirection = 'IN' | 'OUT';

// No status enum exists backend-side; these are the literal values found in
// TransactionEntity — there is no REVERSED/CANCELLED transaction status.
export type TransactionStatus = 'PENDING' | 'COMPLETED' | 'FAILED';

export type FilterTab = 'ALL' | 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER' | 'SUSU';

// Maps FilterTab to the transactionType query param the endpoint accepts.
// 'ALL' sends no filter. The filter is a single exact-match value server-side
// (no OR/IN semantics), so 'SUSU' can only target one of the two susu
// transaction_type values — SUSU_CONTRIBUTION was chosen since it's the
// member-initiated side; SUSU_DISBURSEMENT rows won't match this tab. See
// the PR notes for this known limitation.
export const FILTER_TO_QUERY_PARAM: Record<FilterTab, string | undefined> = {
  ALL: undefined,
  DEPOSIT: 'DEPOSIT',
  WITHDRAWAL: 'WITHDRAWAL',
  TRANSFER: 'TRANSFER',
  SUSU: 'SUSU_CONTRIBUTION',
};

export interface UnifiedTransactionItem {
  transactionReference: string;
  transactionType: string; // DEPOSIT | WITHDRAWAL | TRANSFER | SUSU_CONTRIBUTION | SUSU_DISBURSEMENT
  accountName: string; // vault name / "Wallet" / susu group name
  direction: TransactionDirection;
  amountPesewas: number;
  amountCedis: string;
  narrative: string | null;
  counterpartyName: string | null;
  status: TransactionStatus;
  createdAt: string;
}

export interface UnifiedTransactionPage {
  transactions: UnifiedTransactionItem[];
  nextCursor: string | null;
  hasMore: boolean;
}
