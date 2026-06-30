import { useState, useCallback, useRef } from 'react';
import { paymentsApi } from '../api/payments';
import type { StatementEntry } from '../api/payments';
import type { WalletActivity, TransactionType } from '../types/wallet';

function toCedis(pesewas: number): string {
  return (pesewas / 100).toFixed(2);
}

function normaliseType(raw: string): TransactionType {
  const upper = raw?.toUpperCase() ?? '';
  if (upper.includes('DEPOSIT')) return 'DEPOSIT';
  if (upper.includes('WITHDRAWAL')) return 'WITHDRAWAL';
  if (upper === 'PEER_TRANSFER_FEE') return 'PEER_TRANSFER_FEE';
  if (upper.includes('PEER_TRANSFER')) return 'PEER_TRANSFER';
  if (upper.includes('SUSU_CONTRIBUTION')) return 'SUSU_CONTRIBUTION';
  if (upper.includes('SUSU_DISBURSEMENT')) return 'SUSU_DISBURSEMENT';
  if (upper.includes('VAULT_DEPOSIT')) return 'VAULT_DEPOSIT';
  if (upper.includes('VAULT_WITHDRAWAL')) return 'VAULT_WITHDRAWAL';
  if (upper.includes('EARLY_EXIT')) return 'EARLY_EXIT_PENALTY';
  if (upper.includes('PENALTY')) return 'SUSU_PENALTY';
  return 'OTHER';
}

function toActivity(e: StatementEntry): WalletActivity {
  return {
    id: e.id,
    direction: e.direction,
    amount: e.amount,
    amountCedis: toCedis(e.amount),
    runningBalance: e.running_balance,
    runningBalanceCedis: toCedis(e.running_balance),
    reference: e.transaction_reference,
    narrative: e.narrative,
    transactionType: normaliseType(e.transaction_type),
    createdAt: e.created_at,
    counterparty: e.counterparty,
  };
}

export function useWalletStatement(accountId: string | null) {
  const [entries, setEntries] = useState<WalletActivity[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);

  const cursor = useRef<string | null>(null);

  const fetchPage = useCallback(
    async (isRefresh = false) => {
      if (!accountId) return;
      if (isRefresh) {
        setRefreshing(true);
        cursor.current = null;
      } else {
        setLoading(true);
      }
      setError(null);

      try {
        const page = await paymentsApi.getStatement(accountId, null, 20);
        cursor.current = page.next_cursor;
        setEntries(page.entries.map(toActivity));
        setHasMore(page.has_more);
      } catch {
        setError('Could not load transaction history.');
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [accountId],
  );

  const loadMore = useCallback(async () => {
    if (!accountId || !cursor.current || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await paymentsApi.getStatement(accountId, cursor.current, 20);
      cursor.current = page.next_cursor;
      setEntries(prev => [...prev, ...page.entries.map(toActivity)]);
      setHasMore(page.has_more);
    } catch {
      // Silently fail on load-more; user can scroll up and retry
    } finally {
      setLoadingMore(false);
    }
  }, [accountId, loadingMore]);

  const refresh = useCallback(() => fetchPage(true), [fetchPage]);

  return {
    entries,
    loading,
    loadingMore,
    refreshing,
    error,
    hasMore,
    fetch: fetchPage,
    loadMore,
    refresh,
  };
}
