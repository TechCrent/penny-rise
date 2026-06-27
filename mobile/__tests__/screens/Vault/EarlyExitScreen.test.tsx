import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import axios, { type AxiosResponse } from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import EarlyExitScreen, { EarlyExitStatusPanel } from '../../../src/screens/Vault/EarlyExitScreen';

jest.mock('../../../src/api/hooks/useEarlyExit');
jest.mock('../../../src/api/hooks/useVaultDetail');
jest.mock('../../../src/hooks/useAuth', () => ({
  // Matches the real useAuth() today — no user profile data is available yet,
  // so the MoMo number must always be entered by hand on this screen.
  useAuth: () => ({ user: null }),
}));
const mockGoBack = jest.fn();
const mockNavigate = jest.fn();

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: mockGoBack, navigate: mockNavigate }),
  useRoute: () => ({ params: { vaultId: 'vault-001' } }),
}));

const { useRequestEarlyExit, useCancelEarlyExit } = require('../../../src/api/hooks/useEarlyExit');
const { useVaultDetail } = require('../../../src/api/hooks/useVaultDetail');

const mockRequestAsync = jest.fn();
const mockCancelAsync = jest.fn();

const mockVault = {
  id: 'vault-001',
  name: 'University Fund',
  vault_type: 'LOCKED',
  status: 'ACTIVE',
  ledger_account_id: 'ledger-001',
  balance_pesewas: 100_000,
  balance_cedis: '1,000.00',
  unlock_at: '2028-01-01T00:00:00Z',
  unlock_amount: null,
  unlock_condition_logic: null,
  early_exit_in_progress: false,
  created_at: '2026-01-01T00:00:00Z',
};

const mockExitResult = {
  id: 'exit-001',
  vault_id: 'vault-001',
  reason: 'MEDICAL',
  balance_at_request_pesewas: 100_000,
  balance_at_request_cedis: '1,000.00',
  penalty_amount_pesewas: 5_000,
  penalty_amount_cedis: '50.00',
  release_amount_pesewas: 95_000,
  release_amount_cedis: '950.00',
  scheduled_release_at: new Date(Date.now() + 72 * 60 * 60 * 1000).toISOString(),
  status: 'PENDING',
};

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}
function renderScreen() {
  return render(<EarlyExitScreen />, { wrapper });
}

beforeEach(() => {
  jest.clearAllMocks();
  useVaultDetail.mockReturnValue({ data: mockVault });
  useRequestEarlyExit.mockReturnValue({ mutateAsync: mockRequestAsync, isPending: false });
  useCancelEarlyExit.mockReturnValue({ mutateAsync: mockCancelAsync, isPending: false });
  // Use 73h so formatCountdown floors to 72h during the test
  mockRequestAsync.mockResolvedValue({
    ...mockExitResult,
    scheduled_release_at: new Date(Date.now() + 73 * 60 * 60 * 1000).toISOString(),
  });
  mockCancelAsync.mockResolvedValue(undefined);
});

// ── Reason selection ───────────────────────────────────────────────────────

test('renders reason selector with all four options', () => {
  renderScreen();
  expect(screen.getByText('School fees')).toBeTruthy();
  expect(screen.getByText('Medical emergency')).toBeTruthy();
  expect(screen.getByText('Family need')).toBeTruthy();
  expect(screen.getByText('Something else')).toBeTruthy();
});

test('CTA disabled until a reason is selected', () => {
  renderScreen();
  const cta = screen.getByRole('button', { name: "See what you'll receive" });
  expect(cta.props.accessibilityState?.disabled).toBe(true);
});

test('selecting a reason alone is not enough — CTA stays disabled without a MoMo number', () => {
  renderScreen();
  fireEvent.press(screen.getByRole('radio', { name: /Medical emergency/ }));
  const cta = screen.getByRole('button', { name: "See what you'll receive" });
  expect(cta.props.accessibilityState?.disabled).toBe(true);
});

test('selecting a reason and entering a MoMo number enables CTA', () => {
  renderScreen();
  fireEvent.press(screen.getByRole('radio', { name: /Medical emergency/ }));
  fireEvent.changeText(screen.getByPlaceholderText('0241234567'), '0241234567');
  const cta = screen.getByRole('button', { name: "See what you'll receive" });
  expect(cta.props.accessibilityState?.disabled).toBe(false);
});

test('shows penalty explainer and vault balance on reason screen', () => {
  renderScreen();
  expect(screen.getByText(/5% fee/)).toBeTruthy();
  expect(screen.getByText(/72-hour wait/)).toBeTruthy();
  expect(screen.getByText('1,000.00')).toBeTruthy();
});

// ── Preview screen ─────────────────────────────────────────────────────────

async function advanceToPreview() {
  renderScreen();
  fireEvent.press(screen.getByRole('radio', { name: /Medical emergency/ }));
  fireEvent.changeText(screen.getByPlaceholderText('0241234567'), '0241234567');
  fireEvent.press(screen.getByRole('button', { name: "See what you'll receive" }));
  await screen.findByText("What you'll receive");
}

test('preview shows current balance, fee, and release amount', async () => {
  await advanceToPreview();
  expect(screen.getByText(/GHS 1,000\.00/)).toBeTruthy(); // balance
  expect(screen.getByText(/GHS 50\.00/)).toBeTruthy(); // 5% penalty
  expect(screen.getByText(/GHS 950\.00/)).toBeTruthy(); // release
});

test('preview shows 72-hour cool-off explanation', async () => {
  await advanceToPreview();
  expect(screen.getByText('72-hour cool-off')).toBeTruthy();
  expect(screen.getByText(/cancel at any time/i)).toBeTruthy();
});

test('preview shows note about deposits during cool-off', async () => {
  await advanceToPreview();
  expect(screen.getByText(/Any deposits you make/)).toBeTruthy();
});

test('"Keep my lock" link navigates back', async () => {
  await advanceToPreview();
  const keepLinks = screen.getAllByText(/Keep my lock/);
  fireEvent.press(keepLinks[0]);
  expect(mockGoBack).toHaveBeenCalled();
});

// ── Confirm screen ─────────────────────────────────────────────────────────

async function advanceToConfirm() {
  await advanceToPreview();
  fireEvent.press(screen.getByRole('button', { name: 'Continue to confirmation' }));
  await screen.findByText('Confirm early exit');
}

test('confirm screen shows deliberate action button, not generic "Confirm"', async () => {
  await advanceToConfirm();
  expect(
    screen.getByRole('button', { name: /I understand — start the 72-hour cool-off/ }),
  ).toBeTruthy();
  expect(screen.queryByRole('button', { name: /^Confirm$/ })).toBeNull();
});

test('confirm screen shows vault name, reason, and fee summary', async () => {
  await advanceToConfirm();
  expect(screen.getByText('University Fund')).toBeTruthy();
  expect(screen.getByText('Medical emergency')).toBeTruthy();
  expect(screen.getByText(/GHS 50\.00/)).toBeTruthy();
  expect(screen.getByText(/GHS 950\.00/)).toBeTruthy();
});

test('confirm button submits early-exit request with reason and MoMo details', async () => {
  await advanceToConfirm();
  fireEvent.press(screen.getByRole('button', { name: /I understand/ }));
  await waitFor(() => {
    expect(mockRequestAsync).toHaveBeenCalledWith({
      reason: 'MEDICAL',
      destination_momo_number: '0241234567',
      momo_provider: 'mtn',
    });
  });
});

// ── Done screen ────────────────────────────────────────────────────────────

async function advanceToDone() {
  await advanceToConfirm();
  await act(async () => {
    fireEvent.press(screen.getByRole('button', { name: /I understand/ }));
  });
  await screen.findByText('Cool-off started');
}

test('done screen shows countdown, penalty and release amounts', async () => {
  await advanceToDone();
  expect(screen.getByText(/72h/i)).toBeTruthy();
  expect(screen.getByText('GHS 50.00')).toBeTruthy();
  expect(screen.getByText('GHS 950.00')).toBeTruthy();
});

test('done screen explains cancel option', async () => {
  await advanceToDone();
  expect(screen.getByText(/still cancel from the vault screen/)).toBeTruthy();
});

// ── Server error ───────────────────────────────────────────────────────────

test('VAULT_EARLY_EXIT_ALREADY_PENDING shows inline error on confirm', async () => {
  mockRequestAsync.mockRejectedValue(
    new axios.AxiosError('Conflict', '409', undefined, undefined, {
      status: 409,
      data: { error: { code: 'VAULT_EARLY_EXIT_ALREADY_PENDING', message: 'Already pending.' } },
    } as AxiosResponse),
  );

  await advanceToConfirm();
  await act(async () => {
    fireEvent.press(screen.getByRole('button', { name: /I understand/ }));
  });

  await waitFor(() => {
    expect(screen.getByText(/already in progress/)).toBeTruthy();
  });
  // Stays on confirm screen
  expect(screen.getByText('Confirm early exit')).toBeTruthy();
});

// ── EarlyExitStatusPanel ───────────────────────────────────────────────────

function renderPanel() {
  return render(
    <EarlyExitStatusPanel
      vaultId="vault-001"
      scheduledReleaseAt={new Date(Date.now() + 48 * 60 * 60 * 1000 + 60_000).toISOString()}
      releaseAmountCedis="950.00"
      penaltyAmountCedis="50.00"
    />,
    { wrapper },
  );
}

test('status panel shows countdown, penalty, and release amount', () => {
  renderPanel();
  expect(screen.getByText(/GHS 50\.00/)).toBeTruthy();
  expect(screen.getByText(/GHS 950\.00/)).toBeTruthy();
  expect(screen.getByText(/48h/i)).toBeTruthy();
});

test('status panel has cancel button', () => {
  renderPanel();
  expect(screen.getByRole('button', { name: 'Cancel early exit request' })).toBeTruthy();
});

test('cancel shows confirmation alert', async () => {
  const { Alert } = require('react-native');
  const alertSpy = jest.spyOn(Alert, 'alert');
  renderPanel();
  fireEvent.press(screen.getByRole('button', { name: 'Cancel early exit request' }));
  expect(alertSpy).toHaveBeenCalledWith(
    'Cancel early exit?',
    expect.any(String),
    expect.any(Array),
  );
});
