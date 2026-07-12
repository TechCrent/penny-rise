import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import axios, { type AxiosResponse } from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import WithdrawScreen from '../../../src/screens/Vault/WithdrawScreen';

// ── Mocks ──────────────────────────────────────────────────────────────────

jest.mock('../../../src/api/hooks/useVaultWithdrawal');
jest.mock('../../../src/api/hooks/useVaultDetail');
jest.mock('../../../src/hooks/useAuth', () => ({
  useAuth: () => ({ user: { momoNumber: '0241234567' } }),
}));

// Mutable navigation refs — names start with 'mock' so Jest hoists them.
const mockNavigate = jest.fn();
const mockGoBack = jest.fn();

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: mockNavigate, goBack: mockGoBack }),
  useRoute: () => ({ params: { vaultId: 'vault-001' } }),
}));

const { useVaultWithdrawal } = require('../../../src/api/hooks/useVaultWithdrawal');
const { useVaultDetail } = require('../../../src/api/hooks/useVaultDetail');

const mockMutateAsync = jest.fn();
const mockVault = {
  id: 'vault-001',
  name: 'Emergency Fund',
  vault_type: 'STANDARD',
  status: 'ACTIVE',
  ledger_account_id: 'ledger-001',
  balance_pesewas: 50_000,
  balance_cedis: '500.00',
  unlock_at: null,
  unlock_amount: null,
  unlock_condition_logic: null,
  early_exit_in_progress: false,
  created_at: '2026-06-01T00:00:00Z',
};

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}
function renderWithdraw() {
  return render(<WithdrawScreen />, { wrapper });
}

beforeEach(() => {
  jest.clearAllMocks();
  useVaultDetail.mockReturnValue({ data: mockVault });
  useVaultWithdrawal.mockReturnValue({ mutateAsync: mockMutateAsync, isPending: false });
  mockMutateAsync.mockResolvedValue({
    transaction_reference: 'STSH-202606-WD001',
    paystack_transfer_code: 'TRF_test001',
    status: 'PENDING',
  });
});

// ── Amount entry ───────────────────────────────────────────────────────────

test('renders amount screen with vault balance', () => {
  renderWithdraw();
  expect(screen.getByText('GHS 500.00')).toBeTruthy();
  expect(screen.getByText('Emergency Fund')).toBeTruthy();
  expect(screen.getByPlaceholderText('0.00')).toBeTruthy();
});

test('MoMo number pre-filled from user profile', () => {
  renderWithdraw();
  expect(screen.getByDisplayValue('0241234567')).toBeTruthy();
});

test('shows after-balance preview when amount is valid', () => {
  renderWithdraw();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '100');
  expect(screen.getByText(/After withdrawal: GHS 400.00/)).toBeTruthy();
});

test('exceeds balance: shows inline error and disables CTA', () => {
  renderWithdraw();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '600');
  expect(screen.getByText(/Amount exceeds your balance/)).toBeTruthy();
  const cta = screen.getByRole('button', { name: 'Review withdrawal' });
  expect(cta.props.accessibilityState?.disabled).toBe(true);
});

test('zero amount shows validation error on continue', async () => {
  renderWithdraw();
  fireEvent.press(screen.getByRole('button', { name: 'Review withdrawal' }));
  await waitFor(() => {
    expect(screen.getByText('Enter an amount greater than 0.')).toBeTruthy();
  });
});

test('exact balance amount is allowed (not an error)', () => {
  renderWithdraw();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '500');
  expect(screen.queryByText(/Amount exceeds/)).toBeNull();
  expect(screen.getByText(/After withdrawal: GHS 0.00/)).toBeTruthy();
});

// ── Confirm screen ─────────────────────────────────────────────────────────

async function advanceToConfirm() {
  renderWithdraw();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '100');
  fireEvent.press(screen.getByRole('button', { name: 'Review withdrawal' }));
  await screen.findByText('Confirm withdrawal');
}

test('confirm screen shows vault name, amount, and MoMo destination', async () => {
  await advanceToConfirm();
  expect(screen.getByText('Emergency Fund')).toBeTruthy();
  expect(screen.getByText('GHS 100.00')).toBeTruthy();
  expect(screen.getAllByText(/0241234567/).length).toBeGreaterThan(0);
});

test('confirm screen shows settlement time warning', async () => {
  await advanceToConfirm();
  expect(screen.getByText(/5–10 minutes/)).toBeTruthy();
});

test('confirm button submits withdrawal with correct pesewas', async () => {
  await advanceToConfirm();
  fireEvent.press(screen.getByRole('button', { name: /Withdraw GHS/ }));
  await waitFor(() => {
    expect(mockMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        payload: expect.objectContaining({
          amount: 10_000,
          destination_momo_number: '0241234567',
          momo_provider: 'mtn',
        }),
      }),
    );
  });
});

// ── Pending screen ─────────────────────────────────────────────────────────

async function advanceToPending() {
  await advanceToConfirm();
  fireEvent.press(screen.getByRole('button', { name: /Withdraw GHS/ }));
  await screen.findByText('Transfer in progress');
}

test('pending screen shown after successful submission', async () => {
  await advanceToPending();
  expect(screen.getByText('Transfer in progress')).toBeTruthy();
  expect(screen.getByText('GHS 100.00')).toBeTruthy();
  expect(screen.getByText('STSH-202606-WD001')).toBeTruthy();
});

test('pending screen shows transfer reference selectable for support', async () => {
  await advanceToPending();
  const ref = screen.getByText('STSH-202606-WD001');
  expect(ref.props.selectable).toBe(true);
});

test('pending screen shows "What happens next" explanation', async () => {
  await advanceToPending();
  expect(screen.getByText('What happens next?')).toBeTruthy();
  expect(screen.getByText(/5–10 minutes/)).toBeTruthy();
});

test('user can navigate away from pending screen via Back to vault', async () => {
  await advanceToPending();
  fireEvent.press(screen.getByRole('button', { name: 'Back to vault' }));
  expect(mockNavigate).toHaveBeenCalledWith('VaultDetail', { vaultId: 'vault-001' });
});

test('user can navigate to home screen from pending', async () => {
  await advanceToPending();
  fireEvent.press(screen.getByText('Go to home screen'));
  expect(mockNavigate).toHaveBeenCalledWith('Main', { screen: 'Home' });
});

// ── Idempotency key stability ──────────────────────────────────────────────

test('idempotency key stays the same across retries', async () => {
  mockMutateAsync
    .mockRejectedValueOnce(
      new axios.AxiosError('Service Unavailable', '503', undefined, undefined, {
        status: 503,
        data: { error: { code: 'SERVICE_UNAVAILABLE', message: 'Try again.' } },
      } as AxiosResponse),
    )
    .mockResolvedValue({
      transaction_reference: 'STSH-202606-WD002',
      paystack_transfer_code: 'TRF_002',
      status: 'PENDING',
    });

  await advanceToConfirm();

  fireEvent.press(screen.getByRole('button', { name: /Withdraw GHS/ }));
  await waitFor(() => expect(screen.getByText('Try again.')).toBeTruthy());
  const key1 = mockMutateAsync.mock.calls[0][0].idempotencyKey;

  fireEvent.press(screen.getByRole('button', { name: /Withdraw GHS/ }));
  await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledTimes(2));
  const key2 = mockMutateAsync.mock.calls[1][0].idempotencyKey;

  expect(key1).toEqual(key2);
});

// ── Server errors ──────────────────────────────────────────────────────────

test('VAULT_INSUFFICIENT_BALANCE server error shown inline', async () => {
  mockMutateAsync.mockRejectedValue(
    new axios.AxiosError('Unprocessable Entity', '422', undefined, undefined, {
      status: 422,
      data: { error: { code: 'VAULT_INSUFFICIENT_BALANCE', message: 'Insufficient balance.' } },
    } as AxiosResponse),
  );

  await advanceToConfirm();
  fireEvent.press(screen.getByRole('button', { name: /Withdraw GHS/ }));

  await waitFor(() => {
    expect(screen.getByText(/vault balance is too low/)).toBeTruthy();
  });
  expect(screen.getByText('Confirm withdrawal')).toBeTruthy();
});

test('generic server error shown inline', async () => {
  mockMutateAsync.mockRejectedValue(
    new axios.AxiosError('Internal Server Error', '500', undefined, undefined, {
      status: 500,
      data: { error: { code: 'INTERNAL_ERROR', message: 'Internal error.' } },
    } as AxiosResponse),
  );

  await advanceToConfirm();
  fireEvent.press(screen.getByRole('button', { name: /Withdraw GHS/ }));

  await waitFor(() => {
    expect(screen.getByText('Internal error.')).toBeTruthy();
  });
});
