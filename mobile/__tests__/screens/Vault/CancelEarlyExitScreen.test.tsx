import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import axios, { type AxiosResponse } from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CancelEarlyExitScreen from '../../../src/screens/Vault/CancelEarlyExitScreen';

// ── Mocks ──────────────────────────────────────────────────────────────────

jest.mock('../../../src/api/hooks/useEarlyExit');
jest.mock('../../../src/api/hooks/useVaultDetail');

const mockNavigate = jest.fn();
const mockGoBack = jest.fn();

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: mockNavigate, goBack: mockGoBack }),
  useRoute: () => ({ params: { vaultId: 'vault-001' } }),
}));

const { useCancelEarlyExit } = require('../../../src/api/hooks/useEarlyExit');
const { useVaultDetail } = require('../../../src/api/hooks/useVaultDetail');

const mockCancelAsync = jest.fn();

const mockVault = {
  id: 'vault-001',
  name: 'University Fund',
  vault_type: 'LOCKED',
  status: 'EARLY_EXIT_PENDING',
  ledger_account_id: 'ledger-001',
  balance_pesewas: 100_000,
  balance_cedis: '1,000.00',
  unlock_at: '2028-01-01T00:00:00Z',
  unlock_amount: null,
  unlock_condition_logic: null,
  early_exit_in_progress: true,
  created_at: '2026-01-01T00:00:00Z',
};

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}
function renderScreen() {
  return render(<CancelEarlyExitScreen />, { wrapper });
}

beforeEach(() => {
  jest.clearAllMocks();
  useVaultDetail.mockReturnValue({ data: mockVault });
  useCancelEarlyExit.mockReturnValue({ mutateAsync: mockCancelAsync, isPending: false });
  mockCancelAsync.mockResolvedValue(undefined);
});

test('renders confirmation copy with vault name', () => {
  renderScreen();
  expect(screen.getByText('Cancel this exit request?')).toBeTruthy();
  expect(screen.getByText(/University Fund/)).toBeTruthy();
  expect(screen.getByText(/no fee will be charged/)).toBeTruthy();
});

test('has a cancel-exit button and a keep-exit link', () => {
  renderScreen();
  expect(screen.getByRole('button', { name: 'Cancel early exit request' })).toBeTruthy();
  expect(screen.getByText('Keep my exit request')).toBeTruthy();
});

test('"Keep my exit request" navigates back without cancelling', () => {
  renderScreen();
  fireEvent.press(screen.getByText('Keep my exit request'));
  expect(mockGoBack).toHaveBeenCalled();
  expect(mockCancelAsync).not.toHaveBeenCalled();
});

test('confirming cancellation calls the mutation and navigates to VaultDetail', async () => {
  renderScreen();
  fireEvent.press(screen.getByRole('button', { name: 'Cancel early exit request' }));

  await waitFor(() => expect(mockCancelAsync).toHaveBeenCalled());
  expect(mockNavigate).toHaveBeenCalledWith('VaultDetail', {
    vaultId: 'vault-001',
    successMessage: 'Early exit cancelled.',
  });
});

test('VAULT_NO_PENDING_EARLY_EXIT shows inline error and stays on screen', async () => {
  mockCancelAsync.mockRejectedValue(
    new axios.AxiosError('Not Found', '404', undefined, undefined, {
      status: 404,
      data: { error: { code: 'VAULT_NO_PENDING_EARLY_EXIT', message: 'No pending request.' } },
    } as AxiosResponse),
  );

  renderScreen();
  fireEvent.press(screen.getByRole('button', { name: 'Cancel early exit request' }));

  await waitFor(() => {
    expect(screen.getByText(/already been processed/)).toBeTruthy();
  });
  expect(mockNavigate).not.toHaveBeenCalled();
});

test('generic error message shown when no specific code matches', async () => {
  mockCancelAsync.mockRejectedValue(
    new axios.AxiosError('Internal Server Error', '500', undefined, undefined, {
      status: 500,
      data: { error: { code: 'INTERNAL_ERROR', message: 'Internal error.' } },
    } as AxiosResponse),
  );

  renderScreen();
  fireEvent.press(screen.getByRole('button', { name: 'Cancel early exit request' }));

  await waitFor(() => {
    expect(screen.getByText('Internal error.')).toBeTruthy();
  });
});
