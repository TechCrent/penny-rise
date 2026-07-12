import { renderHook, act, waitFor } from '@testing-library/react-native';
import { useWalletStatement } from '../src/hooks/useWalletStatement';
import { paymentsApi } from '../src/api/payments';

jest.mock('../src/api/payments', () => ({
  paymentsApi: { getStatement: jest.fn() },
}));

describe('useWalletStatement', () => {
  beforeEach(() => jest.clearAllMocks());

  it('maps the real StatementEntryDto field names (entry_id/amount_pesewas/...) — ' +
     'this used to be a hand-guessed shape (id/amount/running_balance) that never ' +
     'matched what the backend actually sends', async () => {
    (paymentsApi.getStatement as jest.Mock).mockResolvedValue({
      entries: [
        {
          entry_id: 'e-1',
          direction: 'CREDIT',
          amount_pesewas: 5000,
          amount_cedis: '50.00',
          running_balance_pesewas: 15000,
          running_balance_cedis: '150.00',
          transaction_reference: 'STSH-202607-ABC',
          transaction_type: 'DEPOSIT',
          narrative: null,
          created_at: '2026-07-12T10:00:00Z',
        },
      ],
      next_cursor: null,
      has_more: false,
      total_entries_on_page: 1,
    });

    const { result } = renderHook(() => useWalletStatement());

    await act(async () => {
      await result.current.fetch();
    });

    await waitFor(() => expect(result.current.entries).toHaveLength(1));

    expect(result.current.entries[0]).toMatchObject({
      id: 'e-1',
      direction: 'CREDIT',
      amount: 5000,
      amountCedis: '50.00',
      runningBalance: 15000,
      runningBalanceCedis: '150.00',
      reference: 'STSH-202607-ABC',
      transactionType: 'DEPOSIT',
      narrative: '',
    });
  });

  it('calls getStatement with no accountId — the endpoint resolves the caller\'s own wallet server-side', async () => {
    (paymentsApi.getStatement as jest.Mock).mockResolvedValue({
      entries: [],
      next_cursor: null,
      has_more: false,
      total_entries_on_page: 0,
    });

    const { result } = renderHook(() => useWalletStatement());

    await act(async () => {
      await result.current.fetch();
    });

    expect(paymentsApi.getStatement).toHaveBeenCalledWith(null, 20);
  });
});
