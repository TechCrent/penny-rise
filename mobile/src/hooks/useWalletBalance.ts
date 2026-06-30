import { useState, useCallback } from 'react';
import { walletApi } from '../api/susu';
import type { WalletBalance } from '../types/susu';

export function useWalletBalance() {
  const [balance, setBalance] = useState<WalletBalance | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetch = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await walletApi.getBalance();
      setBalance({
        accountId: data.account_id,
        balancePesewas: data.balance_pesewas,
        balanceCedis: data.balance_cedis,
      });
    } catch {
      setError('Could not load wallet balance.');
    } finally {
      setLoading(false);
    }
  }, []);

  return { balance, loading, error, fetch };
}
