import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import VaultDetailScreen from '../../../src/screens/Vault/VaultDetailScreen';

jest.mock('../../../src/api/hooks/useVaultDetail');
jest.mock('../../../src/api/hooks/useStatement');
jest.mock('../../../src/api/hooks/useTransactionDetail');

let mockRouteParams: { vaultId: string; successMessage?: string } = { vaultId: 'vault-001' };

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: jest.fn(), navigate: jest.fn() }),
  useRoute: () => ({ params: mockRouteParams }),
}));

const { useVaultDetail } = require('../../../src/api/hooks/useVaultDetail');
const { useStatement } = require('../../../src/api/hooks/useStatement');
const { useTransactionDetail } = require('../../../src/api/hooks/useTransactionDetail');

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}
function renderScreen() {
  return render(<VaultDetailScreen />, { wrapper });
}

const emptyStatement = {
  data: { pages: [{ entries: [], next_cursor: null, has_more: false, total_entries_on_page: 0 }] },
  isLoading: false,
  isFetchingNextPage: false,
  hasNextPage: false,
  fetchNextPage: jest.fn(),
  refetch: jest.fn(),
};

const baseVault = {
  id: 'vault-001',
  name: 'Emergency Fund',
  vault_type: 'STANDARD',
  status: 'ACTIVE',
  ledger_account_id: 'ledger-001',
  balance_pesewas: 100_000,
  balance_cedis: '1,000.00',
  unlock_at: null,
  unlock_amount: null,
  unlock_condition_logic: null,
  early_exit_in_progress: false,
  created_at: '2026-06-01T00:00:00Z',
};

beforeEach(() => {
  jest.clearAllMocks();
  mockRouteParams = { vaultId: 'vault-001' };
  useStatement.mockReturnValue(emptyStatement);
  useTransactionDetail.mockReturnValue({ data: null, isLoading: false, error: null });
});

// ── STANDARD ACTIVE actions ────────────────────────────────────────────────

test('STANDARD ACTIVE: shows Deposit and Withdraw buttons', () => {
  useVaultDetail.mockReturnValue({
    data: baseVault,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  renderScreen();
  expect(screen.getByRole('button', { name: 'Deposit' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Withdraw' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: /Early exit/ })).toBeNull();
});

// ── LOCKED ACTIVE (conditions not met) ────────────────────────────────────

test('LOCKED ACTIVE (conditions not met): shows Deposit and Early exit buttons', () => {
  const lockedVault = {
    ...baseVault,
    vault_type: 'LOCKED',
    unlock_at: '2028-01-01T00:00:00Z',
    unlock_amount: null,
  };
  useVaultDetail.mockReturnValue({
    data: lockedVault,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  renderScreen();
  expect(screen.getByRole('button', { name: 'Deposit' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Request early exit' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Withdraw' })).toBeNull();
  expect(screen.getByText(/5% penalty/)).toBeTruthy();
});

// ── LOCKED ACTIVE (naturally unlocked) ───────────────────────────────────

test('LOCKED ACTIVE unlocked: shows Deposit and Withdraw with unlocked banner', () => {
  const unlockedVault = {
    ...baseVault,
    vault_type: 'LOCKED',
    unlock_at: '2020-01-01T00:00:00Z',
    unlock_amount: null,
  };
  useVaultDetail.mockReturnValue({
    data: unlockedVault,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  renderScreen();
  expect(screen.getByRole('button', { name: 'Deposit' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Withdraw' })).toBeTruthy();
  expect(screen.getByText(/no penalty/)).toBeTruthy();
  expect(screen.queryByRole('button', { name: /Early exit/ })).toBeNull();
});

// ── EARLY_EXIT_PENDING ────────────────────────────────────────────────────

test('EARLY_EXIT_PENDING: shows cool-off banner and Cancel early exit button', () => {
  const earlyExitVault = {
    ...baseVault,
    vault_type: 'LOCKED',
    status: 'EARLY_EXIT_PENDING',
    unlock_at: '2028-01-01T00:00:00Z',
    early_exit_in_progress: true,
  };
  useVaultDetail.mockReturnValue({
    data: earlyExitVault,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  renderScreen();
  expect(screen.getByText('Early exit in progress')).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Deposit' })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Cancel early exit' })).toBeTruthy();
  expect(screen.queryByRole('button', { name: /Early exit$/ })).toBeNull();
});

// ── Transaction history ────────────────────────────────────────────────────

test('empty statement: shows no-transactions empty state', () => {
  useVaultDetail.mockReturnValue({
    data: baseVault,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  renderScreen();
  expect(screen.getByText('No transactions yet')).toBeTruthy();
});

test('statement entries rendered with correct direction and amount', () => {
  useVaultDetail.mockReturnValue({
    data: baseVault,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  useStatement.mockReturnValue({
    ...emptyStatement,
    data: {
      pages: [
        {
          entries: [
            {
              entry_id: 'e1',
              direction: 'CREDIT',
              amount_pesewas: 10_000,
              amount_cedis: '100.00',
              running_balance_pesewas: 10_000,
              running_balance_cedis: '100.00',
              transaction_reference: 'STSH-202606-ABC',
              transaction_type: 'DEPOSIT',
              narrative: 'First deposit',
              created_at: '2026-06-24T10:00:00Z',
            },
          ],
          next_cursor: null,
          has_more: false,
          total_entries_on_page: 1,
        },
      ],
    },
  });

  renderScreen();
  expect(screen.getByText('+ 100.00')).toBeTruthy();
  expect(screen.getByText('DEPOSIT')).toBeTruthy();
  expect(screen.getByText('First deposit')).toBeTruthy();
});

test('tapping statement row opens receipt modal', async () => {
  useVaultDetail.mockReturnValue({
    data: baseVault,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  useStatement.mockReturnValue({
    ...emptyStatement,
    data: {
      pages: [
        {
          entries: [
            {
              entry_id: 'e1',
              direction: 'CREDIT',
              amount_pesewas: 10_000,
              amount_cedis: '100.00',
              running_balance_pesewas: 10_000,
              running_balance_cedis: '100.00',
              transaction_reference: 'STSH-202606-ABC',
              transaction_type: 'DEPOSIT',
              narrative: null,
              created_at: '2026-06-24T10:00:00Z',
            },
          ],
          next_cursor: null,
          has_more: false,
          total_entries_on_page: 1,
        },
      ],
    },
  });
  useTransactionDetail.mockReturnValue({
    data: {
      reference: 'STSH-202606-ABC',
      transaction_type: 'DEPOSIT',
      status: 'COMPLETED',
      gross_amount_pesewas: 10_000,
      gross_amount_cedis: '100.00',
      fee_amount_pesewas: 0,
      fee_amount_cedis: '0.00',
      net_amount_pesewas: 10_000,
      net_amount_cedis: '100.00',
      initiating_user_id: 'user-001',
      counterparty_user_id: null,
      external_provider: 'PAYSTACK',
      external_reference: null,
      narrative: null,
      created_at: '2026-06-24T10:00:00Z',
      completed_at: '2026-06-24T10:01:00Z',
      entries: [],
    },
    isLoading: false,
    error: null,
  });

  renderScreen();
  fireEvent.press(screen.getByRole('button', { name: /CREDIT GHS 100.00/ }));

  await waitFor(() => {
    expect(screen.getByText('Transaction receipt')).toBeTruthy();
    expect(screen.getByText('STSH-202606-ABC')).toBeTruthy();
  });
});

// ── Balance display ───────────────────────────────────────────────────────

test('null balance shows — placeholder', () => {
  useVaultDetail.mockReturnValue({
    data: { ...baseVault, balance_pesewas: null, balance_cedis: null },
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  renderScreen();
  expect(screen.getByText('—')).toBeTruthy();
});

// ── Success banner ────────────────────────────────────────────────────────

test('success message shown when navigated from creation', () => {
  mockRouteParams = { vaultId: 'vault-001', successMessage: 'Vault created!' };
  useVaultDetail.mockReturnValue({
    data: baseVault,
    isLoading: false,
    isFetching: false,
    refetch: jest.fn(),
  });
  renderScreen();
  expect(screen.getByText(/Vault created!/)).toBeTruthy();
});
