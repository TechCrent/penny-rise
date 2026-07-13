import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import axios, { type AxiosResponse } from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import DepositScreen from '../../../src/screens/Vault/DepositScreen';

jest.mock('expo-web-browser', () => ({
  openBrowserAsync: jest.fn().mockResolvedValue({ type: 'dismiss' }),
}));
jest.mock('../../../src/api/hooks/useVaultDeposit');
jest.mock('../../../src/api/hooks/useWalletDeposit');
jest.mock('../../../src/api/hooks/useVaultDetail');
jest.mock('../../../src/api/hooks/useTransactionPoll');
jest.mock('../../../src/hooks/useAuth', () => ({
  useAuth: () => ({ user: { momoNumber: '0241234567' } }),
}));
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: jest.fn(), navigate: jest.fn() }),
  useRoute: () => ({ params: { vaultId: 'vault-001' } }),
}));

const { useVaultDeposit, useVaultDepositOtp } = require('../../../src/api/hooks/useVaultDeposit');
const {
  useWalletDeposit,
  useWalletDepositOtp,
} = require('../../../src/api/hooks/useWalletDeposit');
const { useVaultDetail } = require('../../../src/api/hooks/useVaultDetail');
const { useTransactionPoll } = require('../../../src/api/hooks/useTransactionPoll');

const mockMutateAsync = jest.fn();
const mockOtpMutateAsync = jest.fn();
const mockVault = {
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

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}
function renderDeposit() {
  return render(<DepositScreen />, { wrapper });
}

beforeEach(() => {
  jest.clearAllMocks();
  useVaultDetail.mockReturnValue({ data: mockVault });
  useVaultDeposit.mockReturnValue({ mutateAsync: mockMutateAsync, isPending: false });
  useVaultDepositOtp.mockReturnValue({ mutateAsync: mockOtpMutateAsync, isPending: false });
  useWalletDeposit.mockReturnValue({ mutateAsync: jest.fn(), isPending: false });
  useWalletDepositOtp.mockReturnValue({ mutateAsync: jest.fn(), isPending: false });
  useTransactionPoll.mockReturnValue({ data: null });
  mockMutateAsync.mockResolvedValue({
    transaction_reference: 'STSH-202606-DEP001',
    authorisation_url: null,
    provider_reference: 'pay_ref_001',
    status: 'PENDING',
    otp_required: false,
  });
  mockOtpMutateAsync.mockResolvedValue({
    transaction_reference: 'STSH-202606-DEP001',
    authorisation_url: null,
    provider_reference: 'pay_ref_001',
    status: 'PENDING',
    otp_required: false,
  });
});

// ── Amount entry ───────────────────────────────────────────────────────────

test('renders amount entry screen with vault context', () => {
  renderDeposit();
  expect(screen.getByText('Emergency Fund')).toBeTruthy();
  expect(screen.getByPlaceholderText('0.00')).toBeTruthy();
  expect(screen.getByText('Continue')).toBeTruthy();
});

test('quick-amount pill sets amount field', () => {
  renderDeposit();
  fireEvent.press(screen.getByRole('button', { name: 'Set amount to GHS 100' }));
  expect(screen.getByDisplayValue('100')).toBeTruthy();
});

test('amount below minimum shows validation error', async () => {
  renderDeposit();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '0.5');
  fireEvent.press(screen.getByText('Continue'));
  await waitFor(() => {
    expect(screen.getByText(/Minimum deposit is GHS 1.00/)).toBeTruthy();
  });
});

test('empty amount shows validation error', async () => {
  renderDeposit();
  fireEvent.press(screen.getByText('Continue'));
  await waitFor(() => {
    expect(screen.getByText(/Minimum deposit/)).toBeTruthy();
  });
});

test('fee row shows "Free"', () => {
  renderDeposit();
  expect(screen.getByText('Free')).toBeTruthy();
});

// ── Method selection ───────────────────────────────────────────────────────

test('tapping Continue from valid amount advances to method screen', async () => {
  renderDeposit();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '50');
  fireEvent.press(screen.getByText('Continue'));
  await waitFor(() => {
    expect(screen.getByText('Payment method')).toBeTruthy();
    expect(screen.getByText('Mobile Money')).toBeTruthy();
  });
});

test('CARD option shows "Coming soon" badge', async () => {
  renderDeposit();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '50');
  fireEvent.press(screen.getByText('Continue'));
  await waitFor(() => {
    expect(screen.getByText('Coming soon')).toBeTruthy();
  });
});

test('MoMo number is pre-filled from user profile', async () => {
  renderDeposit();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '50');
  fireEvent.press(screen.getByText('Continue'));
  await waitFor(() => {
    expect(screen.getByDisplayValue('0241234567')).toBeTruthy();
  });
});

// ── Confirmation ───────────────────────────────────────────────────────────

async function advanceToConfirm() {
  renderDeposit();
  fireEvent.changeText(screen.getByPlaceholderText('0.00'), '100');
  fireEvent.press(screen.getByText('Continue'));
  await screen.findByText('Payment method');
  fireEvent.press(screen.getByText('Review deposit'));
  await screen.findByText('Confirm deposit');
}

test('confirmation screen shows vault name, amount, payment method', async () => {
  await advanceToConfirm();
  expect(screen.getByText('Emergency Fund')).toBeTruthy();
  expect(screen.getByText('GHS 100.00')).toBeTruthy();
  expect(screen.getByText(/0241234567/)).toBeTruthy();
});

test('confirm button calls deposit mutation with correct pesewas', async () => {
  await advanceToConfirm();
  fireEvent.press(screen.getByRole('button', { name: /Confirm deposit/ }));
  await waitFor(() => {
    expect(mockMutateAsync).toHaveBeenCalledWith(
      expect.objectContaining({
        payload: expect.objectContaining({
          amount: 10_000,
          payment_method: 'MOMO',
        }),
      }),
    );
  });
});

// ── Idempotency key stability ──────────────────────────────────────────────

test('idempotency key stays the same across retries', async () => {
  const err503 = new axios.AxiosError('Service Unavailable', '503', undefined, undefined, {
    status: 503,
    data: { error: { code: 'SERVICE_UNAVAILABLE', message: 'Retry.' } },
  } as AxiosResponse);

  mockMutateAsync.mockRejectedValue(err503);

  await advanceToConfirm();
  await act(async () => {
    fireEvent.press(screen.getByRole('button', { name: /Confirm deposit/ }));
  });

  await waitFor(() => {
    expect(screen.getByText(/Payment service is unavailable/)).toBeTruthy();
  });

  const key1 = mockMutateAsync.mock.calls[0][0].idempotencyKey;

  mockMutateAsync.mockResolvedValue({
    transaction_reference: 'STSH-202606-DEP002',
    authorisation_url: null,
    provider_reference: 'r',
    status: 'PENDING',
    otp_required: false,
  });

  await act(async () => {
    fireEvent.press(screen.getByRole('button', { name: /Confirm deposit/ }));
  });
  await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledTimes(2));

  const key2 = mockMutateAsync.mock.calls[1][0].idempotencyKey;
  expect(key1).toEqual(key2);
});

// ── Success screen ─────────────────────────────────────────────────────────

test('success screen shown when transaction COMPLETED', async () => {
  // Only return data once txnRef is set and polling is active; null otherwise.
  useTransactionPoll.mockImplementation((ref: string | null, enabled: boolean) => ({
    data:
      ref && enabled
        ? {
            transaction_reference: 'STSH-202606-DEP001',
            status: 'COMPLETED',
            gross_amount_pesewas: 10_000,
            gross_amount_cedis: '100.00',
            transaction_type: 'DEPOSIT',
            fee_amount_pesewas: 0,
            net_amount_pesewas: 10_000,
            entries: [],
            created_at: '2026-06-24T10:00:00Z',
            posted_at: '2026-06-24T10:01:00Z',
          }
        : null,
  }));

  await advanceToConfirm();
  await act(async () => {
    fireEvent.press(screen.getByRole('button', { name: /Confirm deposit/ }));
  });

  await waitFor(() => {
    expect(screen.getByText('Deposit successful!')).toBeTruthy();
    expect(screen.getByText('GHS 100.00')).toBeTruthy();
    expect(screen.getByText(/Emergency Fund/)).toBeTruthy();
  });
});

// ── Failure screen ─────────────────────────────────────────────────────────

test('failure screen shown when transaction FAILED', async () => {
  useTransactionPoll.mockImplementation((ref: string | null, enabled: boolean) => ({
    data:
      ref && enabled
        ? {
            transaction_reference: 'STSH-202606-DEP001',
            status: 'FAILED',
            transaction_type: 'DEPOSIT',
            gross_amount_pesewas: 10_000,
            gross_amount_cedis: '100.00',
            fee_amount_pesewas: 0,
            net_amount_pesewas: 10_000,
            entries: [],
            created_at: '2026-06-24T10:00:00Z',
            posted_at: null,
          }
        : null,
  }));

  await advanceToConfirm();
  await act(async () => {
    fireEvent.press(screen.getByRole('button', { name: /Confirm deposit/ }));
  });

  await waitFor(() => {
    expect(screen.getByText("Deposit wasn't completed")).toBeTruthy();
    expect(screen.getByText('Try again')).toBeTruthy();
  });
});

test('failure screen retry button returns to confirm phase', async () => {
  useTransactionPoll.mockImplementation((ref: string | null, enabled: boolean) => ({
    data:
      ref && enabled
        ? {
            transaction_reference: 'STSH-202606-DEP001',
            status: 'FAILED',
            transaction_type: 'DEPOSIT',
            gross_amount_pesewas: 10_000,
            gross_amount_cedis: '100.00',
            fee_amount_pesewas: 0,
            net_amount_pesewas: 10_000,
            entries: [],
            created_at: '2026-06-24T10:00:00Z',
            posted_at: null,
          }
        : null,
  }));

  await advanceToConfirm();
  await act(async () => {
    fireEvent.press(screen.getByRole('button', { name: /Confirm deposit/ }));
  });

  await waitFor(() => screen.getByText('Try again'));
  fireEvent.press(screen.getByText('Try again'));

  await waitFor(() => {
    expect(screen.getByText('Confirm deposit')).toBeTruthy();
  });
});

// ── Server error ───────────────────────────────────────────────────────────

test('server error displayed inline on confirm screen', async () => {
  mockMutateAsync.mockRejectedValue(
    new axios.AxiosError('Forbidden', '403', undefined, undefined, {
      status: 403,
      data: { error: { code: 'FORBIDDEN', message: 'Permission denied.' } },
    } as AxiosResponse),
  );

  await advanceToConfirm();
  fireEvent.press(screen.getByRole('button', { name: /Confirm deposit/ }));

  await waitFor(() => {
    expect(screen.getByText(/don't have permission/)).toBeTruthy();
  });
  expect(screen.getByText('Confirm deposit')).toBeTruthy();
});

// ── OTP phase ──────────────────────────────────────────────────────────────

test('otp_required true shows verification code input', async () => {
  mockMutateAsync.mockResolvedValue({
    transaction_reference: 'STSH-202606-DEP001',
    authorisation_url: null,
    provider_reference: 'pay_ref_001',
    status: 'PENDING',
    otp_required: true,
  });

  await advanceToConfirm();
  await act(async () => {
    fireEvent.press(screen.getByRole('button', { name: /Confirm deposit/ }));
  });

  await waitFor(() => {
    expect(screen.getByText('Enter verification code')).toBeTruthy();
    expect(screen.getByLabelText('Verification code')).toBeTruthy();
    expect(screen.getByText('Verify and continue')).toBeTruthy();
  });
  expect(mockOtpMutateAsync).not.toHaveBeenCalled();
});
