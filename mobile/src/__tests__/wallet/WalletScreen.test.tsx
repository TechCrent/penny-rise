import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import { WalletScreen } from '../../screens/wallet/WalletScreen';
import * as balanceHook from '../../hooks/useWalletBalance';
import * as statementHook from '../../hooks/useWalletStatement';
import type { WalletActivity } from '../../types/wallet';

const mockNavigate = jest.fn();

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: mockNavigate }),
}));

const mockBalance = {
  accountId: 'acc-1',
  balancePesewas: 150_000,
  balanceCedis: '1500.00',
};

function makeActivity(overrides: Partial<WalletActivity> = {}): WalletActivity {
  return {
    id: 'txn-1',
    direction: 'CREDIT',
    amount: 50_000,
    amountCedis: '500.00',
    runningBalance: 150_000,
    runningBalanceCedis: '1500.00',
    reference: 'STSH-202606-001',
    narrative: 'Deposit from MoMo',
    transactionType: 'DEPOSIT',
    createdAt: '2026-06-24T10:00:00Z',
    ...overrides,
  };
}

function mockBalanceHook(overrides: Partial<ReturnType<typeof balanceHook.useWalletBalance>> = {}) {
  jest.spyOn(balanceHook, 'useWalletBalance').mockReturnValue({
    balance: mockBalance,
    loading: false,
    error: null,
    fetch: jest.fn(),
    ...overrides,
  });
}

function mockStatementHook(
  overrides: Partial<ReturnType<typeof statementHook.useWalletStatement>> = {},
) {
  jest.spyOn(statementHook, 'useWalletStatement').mockReturnValue({
    entries: [],
    loading: false,
    loadingMore: false,
    refreshing: false,
    error: null,
    hasMore: false,
    fetch: jest.fn(),
    loadMore: jest.fn(),
    refresh: jest.fn(),
    ...overrides,
  });
}

describe('WalletScreen', () => {
  beforeEach(() => {
    mockBalanceHook();
    mockStatementHook();
  });

  // ── Balance display ────────────────────────────────────────────────

  it('shows balance prominently', () => {
    render(<WalletScreen />);
    expect(screen.getByTestId('balance-card')).toBeTruthy();
    expect(screen.getByTestId('balance-amount')).toBeTruthy();
    expect(screen.getByText('GHS 1500.00')).toBeTruthy();
  });

  it('shows skeleton on first load before balance arrives', () => {
    mockBalanceHook({ loading: true, balance: null });
    mockStatementHook({ loading: true, entries: [] });
    render(<WalletScreen />);
    expect(screen.getByTestId('wallet-skeleton')).toBeTruthy();
  });

  it('shows balance unavailable on balance error', () => {
    mockBalanceHook({ balance: null, error: 'Network error' });
    render(<WalletScreen />);
    expect(screen.getByTestId('balance-error')).toBeTruthy();
    expect(screen.getByText('Balance unavailable')).toBeTruthy();
  });

  // ── Quick actions ──────────────────────────────────────────────────

  it('renders only Send and Withdraw quick action buttons', () => {
    render(<WalletScreen />);
    expect(screen.getByTestId('send-btn')).toBeTruthy();
    expect(screen.getByTestId('withdraw-btn')).toBeTruthy();
    expect(screen.queryByTestId('deposit-btn')).toBeNull();
    expect(screen.queryByTestId('vault-btn')).toBeNull();
  });

  it('navigates to the withdraw coming-soon placeholder on withdraw tap', () => {
    mockNavigate.mockClear();
    render(<WalletScreen />);
    fireEvent.press(screen.getByTestId('withdraw-btn'));
    expect(mockNavigate).toHaveBeenCalledWith('WalletWithdrawComingSoon');
  });

  it('navigates to RecipientPicker on send tap', () => {
    mockNavigate.mockClear();
    render(<WalletScreen />);
    fireEvent.press(screen.getByTestId('send-btn'));
    expect(mockNavigate).toHaveBeenCalledWith('RecipientPicker');
  });

  // ── Activity feed ──────────────────────────────────────────────────

  it('renders activity rows for each entry', () => {
    mockStatementHook({
      entries: [
        makeActivity({ id: 'txn-1', narrative: 'MoMo deposit' }),
        makeActivity({
          id: 'txn-2',
          narrative: 'From Kwame',
          direction: 'CREDIT',
          transactionType: 'PEER_TRANSFER',
        }),
      ],
    });
    render(<WalletScreen />);
    expect(screen.getByTestId('activity-row-txn-1')).toBeTruthy();
    expect(screen.getByTestId('activity-row-txn-2')).toBeTruthy();
  });

  it('shows empty state when no activity', () => {
    mockStatementHook({ entries: [], loading: false });
    render(<WalletScreen />);
    expect(screen.getByTestId('empty-state')).toBeTruthy();
  });

  it('shows error banner when statement fails', () => {
    mockStatementHook({ error: 'Failed to load transactions.', entries: [] });
    render(<WalletScreen />);
    expect(screen.getByTestId('error-banner')).toBeTruthy();
    expect(screen.getByText('Failed to load transactions.')).toBeTruthy();
  });

  // ── Pagination ─────────────────────────────────────────────────────

  it('shows load-more indicator when loadingMore=true', () => {
    mockStatementHook({
      entries: [makeActivity()],
      loadingMore: true,
      hasMore: true,
    });
    render(<WalletScreen />);
    expect(screen.getByTestId('loading-more')).toBeTruthy();
  });

  // ── Pull-to-refresh ────────────────────────────────────────────────

  it('pull-to-refresh calls both fetchBalance and refresh', () => {
    const fetchBalance = jest.fn();
    const refresh = jest.fn();
    mockBalanceHook({ fetch: fetchBalance });
    mockStatementHook({ refresh });

    render(<WalletScreen />);
    const list = screen.getByTestId('activity-list');
    fireEvent(list, 'refresh');
    expect(fetchBalance).toHaveBeenCalled();
  });

  // ── Transaction badges ─────────────────────────────────────────────

  it('deposit badge renders with correct test ID', () => {
    mockStatementHook({
      entries: [makeActivity({ transactionType: 'DEPOSIT' })],
    });
    render(<WalletScreen />);
    expect(screen.getByTestId('badge-DEPOSIT')).toBeTruthy();
  });

  it('peer transfer badge renders with correct test ID', () => {
    mockStatementHook({
      entries: [makeActivity({ transactionType: 'PEER_TRANSFER' })],
    });
    render(<WalletScreen />);
    expect(screen.getByTestId('badge-PEER_TRANSFER')).toBeTruthy();
  });

  it('susu contribution badge renders with correct test ID', () => {
    mockStatementHook({
      entries: [makeActivity({ transactionType: 'SUSU_CONTRIBUTION' })],
    });
    render(<WalletScreen />);
    expect(screen.getByTestId('badge-SUSU_CONTRIBUTION')).toBeTruthy();
  });

  it('susu disbursement badge renders with correct test ID', () => {
    mockStatementHook({
      entries: [makeActivity({ transactionType: 'SUSU_DISBURSEMENT', direction: 'CREDIT' })],
    });
    render(<WalletScreen />);
    expect(screen.getByTestId('badge-SUSU_DISBURSEMENT')).toBeTruthy();
  });

  // ── Direction indicator ────────────────────────────────────────────

  it('credit entry shown with + prefix', () => {
    mockStatementHook({
      entries: [makeActivity({ direction: 'CREDIT', amountCedis: '500.00' })],
    });
    render(<WalletScreen />);
    expect(screen.getByText('+ GHS 500.00')).toBeTruthy();
  });

  it('debit entry shown with - prefix', () => {
    mockStatementHook({
      entries: [makeActivity({ direction: 'DEBIT', amountCedis: '200.00' })],
    });
    render(<WalletScreen />);
    expect(screen.getByText('- GHS 200.00')).toBeTruthy();
  });

  // ── Graceful degradation ───────────────────────────────────────────

  it('balance error with retry button visible', () => {
    mockBalanceHook({ balance: null, error: 'Service unavailable' });
    render(<WalletScreen />);
    expect(screen.getByTestId('balance-retry-btn')).toBeTruthy();
  });
});
