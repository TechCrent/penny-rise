import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { RefreshControl } from 'react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TransactionHistoryScreen } from '../../../src/screens/TransactionHistory/TransactionHistoryScreen';
import * as transactionHistoryApi from '../../../src/api/transactionHistoryApi';
import * as useTransactionDetailModule from '../../../src/api/hooks/useTransactionDetail';
import type { UnifiedTransactionItem } from '../../../src/screens/TransactionHistory/types';

jest.mock('../../../src/api/transactionHistoryApi');
jest.mock('../../../src/api/hooks/useTransactionDetail');

const mockGoBack = jest.fn();
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ goBack: mockGoBack }),
  useRoute: () => ({ params: undefined }),
}));

const baseTransaction: UnifiedTransactionItem = {
  transactionReference: 'STSH-202607-000001',
  transactionType: 'DEPOSIT',
  accountName: 'Emergency Fund',
  direction: 'IN',
  amountPesewas: 20000,
  amountCedis: '200.00',
  narrative: null,
  counterpartyName: null,
  status: 'COMPLETED',
  createdAt: '2026-07-01T09:00:00Z',
};

function renderScreen() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TransactionHistoryScreen />
    </QueryClientProvider>,
  );
}

describe('TransactionHistoryScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (useTransactionDetailModule.useTransactionDetail as jest.Mock).mockReturnValue({
      data: undefined,
      isLoading: false,
    });
  });

  it('renders a list of transaction rows from the API', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [baseTransaction],
      nextCursor: null,
      hasMore: false,
    });

    renderScreen();

    expect(await screen.findByText('Emergency Fund')).toBeTruthy();
    expect(screen.getByText('+GHS 200.00')).toBeTruthy();
  });

  it('shows wallet, vault, and susu entries from different account types in the same feed', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [
        {
          ...baseTransaction,
          transactionReference: 'STSH-202607-000001',
          accountName: 'Emergency Fund',
        },
        {
          ...baseTransaction,
          transactionReference: 'STSH-202607-000002',
          accountName: 'Wallet',
          transactionType: 'WITHDRAWAL',
          direction: 'OUT',
          amountCedis: '50.00',
        },
        {
          ...baseTransaction,
          transactionReference: 'STSH-202607-000003',
          accountName: 'Legon Roommates',
          transactionType: 'SUSU_CONTRIBUTION',
          direction: 'OUT',
          amountCedis: '100.00',
        },
      ],
      nextCursor: null,
      hasMore: false,
    });

    renderScreen();

    await screen.findByText('Emergency Fund');
    expect(screen.getByText('Wallet')).toBeTruthy();
    expect(screen.getByText('Legon Roommates')).toBeTruthy();
  });

  it('PENDING transaction shows the pending indicator badge', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [{ ...baseTransaction, status: 'PENDING' }],
      nextCursor: null,
      hasMore: false,
    });

    renderScreen();

    await waitFor(() => expect(screen.getByTestId('pending-indicator')).toBeTruthy());
  });

  it('completed transaction does NOT show a pending indicator', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [baseTransaction],
      nextCursor: null,
      hasMore: false,
    });

    renderScreen();
    await screen.findByText('Emergency Fund');
    expect(screen.queryByTestId('pending-indicator')).toBeNull();
  });

  it('switching filter tab re-fetches with the appropriate transactionType param', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [],
      nextCursor: null,
      hasMore: false,
    });

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Withdrawals filter tab'));

    await waitFor(() => {
      expect(transactionHistoryApi.fetchUnifiedTransactions).toHaveBeenCalledWith(
        expect.objectContaining({ transactionType: 'WITHDRAWAL' }),
      );
    });
  });

  it('shows an empty state specific to the active filter tab', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [],
      nextCursor: null,
      hasMore: false,
    });

    renderScreen();

    await screen.findByText('No transactions yet');

    fireEvent.press(screen.getByLabelText('Deposits filter tab'));
    await screen.findByText('No deposits yet');
  });

  it('tapping a row opens the receipt modal', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [baseTransaction],
      nextCursor: null,
      hasMore: false,
    });

    renderScreen();
    fireEvent.press(await screen.findByText('Emergency Fund'));

    await waitFor(() => expect(screen.getByTestId('receipt-reference')).toBeTruthy());
    expect(screen.getByTestId('receipt-amount')).toBeTruthy();
  });

  it('receipt modal shows a fee row when the live detail fetch reports a non-zero fee (PENDING row)', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [{ ...baseTransaction, status: 'PENDING' }],
      nextCursor: null,
      hasMore: false,
    });
    (useTransactionDetailModule.useTransactionDetail as jest.Mock).mockReturnValue({
      data: {
        reference: 'STSH-202607-000001',
        transaction_type: 'DEPOSIT',
        status: 'COMPLETED',
        gross_amount_pesewas: 21000,
        gross_amount_cedis: '210.00',
        fee_amount_pesewas: 1000,
        fee_amount_cedis: '10.00',
        net_amount_pesewas: 20000,
        net_amount_cedis: '200.00',
        initiating_user_id: 'user-001',
        counterparty_user_id: null,
        external_provider: null,
        external_reference: null,
        narrative: null,
        created_at: '2026-07-01T09:00:00Z',
        completed_at: '2026-07-01T09:00:05Z',
        entries: [],
      },
      isLoading: false,
    });

    renderScreen();
    fireEvent.press(await screen.findByText('Emergency Fund'));

    expect(await screen.findByTestId('receipt-fee')).toBeTruthy();
  });

  it('pull-to-refresh triggers a re-fetch', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock).mockResolvedValue({
      transactions: [baseTransaction],
      nextCursor: null,
      hasMore: false,
    });

    renderScreen();
    await screen.findByText('Emergency Fund');
    expect(transactionHistoryApi.fetchUnifiedTransactions).toHaveBeenCalledTimes(1);

    const refreshControl = screen.UNSAFE_getByType(RefreshControl);
    refreshControl.props.onRefresh();

    await waitFor(() =>
      expect(transactionHistoryApi.fetchUnifiedTransactions).toHaveBeenCalledTimes(2),
    );
  });

  it('load-more triggers fetchNextPage when scrolled to the bottom', async () => {
    (transactionHistoryApi.fetchUnifiedTransactions as jest.Mock)
      .mockResolvedValueOnce({
        transactions: [baseTransaction],
        nextCursor: 'STSH-202607-000001',
        hasMore: true,
      })
      .mockResolvedValueOnce({
        transactions: [
          {
            ...baseTransaction,
            transactionReference: 'STSH-202607-000002',
            accountName: 'Savings',
          },
        ],
        nextCursor: null,
        hasMore: false,
      });

    renderScreen();
    await screen.findByText('Emergency Fund');

    fireEvent(screen.UNSAFE_getByType(require('react-native').FlatList), 'onEndReached');

    await waitFor(() => expect(screen.getByText('Savings')).toBeTruthy());
  });
});
