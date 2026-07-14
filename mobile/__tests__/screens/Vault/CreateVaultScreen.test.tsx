import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import axios, { type AxiosResponse } from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import CreateVaultScreen from '../../../src/screens/Vault/CreateVaultScreen';

// ── Mocks ──────────────────────────────────────────────────────────────────

jest.mock('../../../src/api/hooks/useCreateVault');
jest.mock('../../../src/api/hooks/useVaults');

const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: jest.fn(), replace: jest.fn(), navigate: mockNavigate }),
}));

const { useCreateVault } = require('../../../src/api/hooks/useCreateVault');
const { useVaults } = require('../../../src/api/hooks/useVaults');

const mockMutateAsync = jest.fn();

function defaultVaultHook(overrides = {}) {
  return {
    data: { vaults: [], total_count: 0, balance_unavailable_count: 0 },
    isLoading: false,
    ...overrides,
  };
}

function wrapper({ children }: { children: React.ReactNode }) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

function renderCreate() {
  return render(<CreateVaultScreen />, { wrapper });
}

// ─────────────────────────────────────────────────────────────────────────────

describe('CreateVaultScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useVaults.mockReturnValue(defaultVaultHook());
    useCreateVault.mockReturnValue({ mutateAsync: mockMutateAsync, isPending: false });
    mockMutateAsync.mockResolvedValue({
      id: 'new-vault-001',
      name: 'Test Vault',
      vault_type: 'STANDARD',
      status: 'ACTIVE',
      ledger_account_id: 'ledger-001',
      created_at: '2026-06-24T00:00:00Z',
    });
  });

  // ── Step 1 rendering ───────────────────────────────────────────────────

  it('renders step 1 with name input and type cards', () => {
    renderCreate();
    expect(screen.getByText('Create a vault')).toBeTruthy();
    expect(screen.getByPlaceholderText(/Emergency fund/)).toBeTruthy();
    expect(screen.getByText('Standard vault')).toBeTruthy();
    expect(screen.getByText('Locked vault')).toBeTruthy();
  });

  it('shows character counter for name input', () => {
    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'My Vault');
    expect(screen.getByText('8/100')).toBeTruthy();
  });

  // ── STANDARD vault creation ────────────────────────────────────────────

  it('submits STANDARD vault directly from step 1', async () => {
    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'Emergency Fund');
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            name: 'Emergency Fund',
            vault_type: 'STANDARD',
          }),
        }),
      );
    });
  });

  it('requires a vault name before proceeding', async () => {
    renderCreate();
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    await waitFor(() => {
      expect(screen.getByText('Vault name is required.')).toBeTruthy();
    });
    expect(mockMutateAsync).not.toHaveBeenCalled();
  });

  // ── LOCKED vault flow ──────────────────────────────────────────────────

  it('tapping LOCKED type then Next navigates to step 2', async () => {
    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'University Fund');
    fireEvent.press(screen.getByText('Locked vault'));
    fireEvent.press(screen.getByRole('button', { name: /Next/ }));

    await waitFor(() => {
      expect(screen.getByText('Unlock conditions')).toBeTruthy();
      expect(screen.getByText('Early exit penalty')).toBeTruthy();
    });
  });

  it('LOCKED vault — date only: submits correctly', async () => {
    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'Car Fund');
    fireEvent.press(screen.getByText('Locked vault'));
    fireEvent.press(screen.getByRole('button', { name: /Next/ }));

    await screen.findByText('Unlock conditions');
    fireEvent.changeText(screen.getByPlaceholderText('dd/mm/yyyy'), '01/01/2028');
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            vault_type: 'LOCKED',
            unlock_at: expect.stringContaining('2028'),
            unlock_amount: null,
            unlock_condition_logic: null,
          }),
        }),
      );
    });
  });

  it('LOCKED vault — amount only: submits correctly', async () => {
    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'House Fund');
    fireEvent.press(screen.getByText('Locked vault'));
    fireEvent.press(screen.getByRole('button', { name: /Next/ }));

    await screen.findByText('Unlock conditions');
    fireEvent.changeText(screen.getByPlaceholderText('e.g. 5000.00'), '10000');
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            vault_type: 'LOCKED',
            unlock_at: null,
            unlock_amount: 1_000_000, // GHS 10 000 → pesewas
          }),
        }),
      );
    });
  });

  it('LOCKED vault — both conditions: shows AND/OR toggle and submits', async () => {
    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'Dream Fund');
    fireEvent.press(screen.getByText('Locked vault'));
    fireEvent.press(screen.getByRole('button', { name: /Next/ }));

    await screen.findByText('Unlock conditions');
    fireEvent.changeText(screen.getByPlaceholderText('dd/mm/yyyy'), '01/01/2028');
    fireEvent.changeText(screen.getByPlaceholderText('e.g. 5000.00'), '5000');

    await screen.findByText('Both conditions'); // AND/OR toggle appears

    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: expect.objectContaining({
            unlock_condition_logic: 'AND',
          }),
        }),
      );
    });
  });

  it('requires at least one condition for LOCKED vault', async () => {
    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'Empty Lock');
    fireEvent.press(screen.getByText('Locked vault'));
    fireEvent.press(screen.getByRole('button', { name: /Next/ }));

    await screen.findByText('Unlock conditions');
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    await waitFor(() => {
      expect(screen.getByText(/At least one unlock condition/)).toBeTruthy();
    });
    expect(mockMutateAsync).not.toHaveBeenCalled();
  });

  // ── Free-tier limit ────────────────────────────────────────────────────

  it('disables CTA when STANDARD vault limit is reached', () => {
    useVaults.mockReturnValue(
      defaultVaultHook({
        data: {
          vaults: [
            { vault_type: 'STANDARD', status: 'ACTIVE' },
            { vault_type: 'STANDARD', status: 'ACTIVE' },
          ],
          total_count: 2,
          balance_unavailable_count: 0,
        },
      }),
    );

    renderCreate();
    const cta = screen.getByRole('button', { name: /Create vault/ });
    expect(cta.props.accessibilityState?.disabled).toBe(true);
    expect(screen.getByText(/free.*vault allowance/i)).toBeTruthy();
  });

  it('shows limit banner on LOCKED card when locked limit reached', () => {
    useVaults.mockReturnValue(
      defaultVaultHook({
        data: {
          vaults: [{ vault_type: 'LOCKED', status: 'ACTIVE' }],
          total_count: 1,
          balance_unavailable_count: 0,
        },
      }),
    );

    renderCreate();
    expect(screen.getByText(/Free plan: 1 locked vault maximum/)).toBeTruthy();
  });

  // ── Server errors ──────────────────────────────────────────────────────

  it('shows inline server error on submission failure', async () => {
    mockMutateAsync.mockRejectedValue(
      new axios.AxiosError('Internal Server Error', '500', undefined, undefined, {
        status: 500,
        data: {
          error: { code: 'INTERNAL_ERROR', message: 'Internal server error — please retry.' },
        },
      } as AxiosResponse),
    );

    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'Fund');
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    await waitFor(() => {
      expect(screen.getByText(/Internal server error/)).toBeTruthy();
    });
  });

  it('shows an upgrade link on VAULT_TIER_LIMIT_EXCEEDED (v0.5-031 fix — was VAULT_FREE_TIER_LIMIT_REACHED, never matched)', async () => {
    mockMutateAsync.mockRejectedValue(
      new axios.AxiosError('Unprocessable Entity', '422', undefined, undefined, {
        status: 422,
        data: {
          error: {
            code: 'VAULT_TIER_LIMIT_EXCEEDED',
            message: "You've reached your plan's limit of 2 STANDARD vault(s).",
            details: { upgrade_url: 'pennyrise://subscription/upgrade' },
          },
        },
      } as AxiosResponse),
    );

    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'Fund');
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    const upgradeLink = await screen.findByLabelText('Upgrade to Premium');
    fireEvent.press(upgradeLink);

    expect(mockNavigate).toHaveBeenCalledWith('SubscriptionUpgrade');
  });

  it('shows KYC error when 403 returned', async () => {
    mockMutateAsync.mockRejectedValue(
      new axios.AxiosError('Forbidden', '403', undefined, undefined, {
        status: 403,
        data: { error: { code: 'KYC_NOT_APPROVED', message: 'KYC required' } },
      } as AxiosResponse),
    );

    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'Fund');
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));

    await waitFor(() => {
      expect(screen.getByText(/KYC verification must be approved/)).toBeTruthy();
    });
  });

  // ── Penalty warning ────────────────────────────────────────────────────

  it('penalty warning visible on LOCKED vault step 2', async () => {
    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'School Fund');
    fireEvent.press(screen.getByText('Locked vault'));
    fireEvent.press(screen.getByRole('button', { name: /Next/ }));

    await screen.findByText('Early exit penalty');
    expect(screen.getByText(/5% penalty/)).toBeTruthy();
  });

  // ── Idempotency key stability ──────────────────────────────────────────

  it('same idempotency key used across retries within same form session', async () => {
    mockMutateAsync
      .mockRejectedValueOnce(
        new axios.AxiosError('Service Unavailable', '503', undefined, undefined, {
          status: 503,
          data: { error: { code: 'SERVICE_UNAVAILABLE', message: 'Try again.' } },
        } as AxiosResponse),
      )
      .mockResolvedValue({
        id: 'new-vault-001',
        name: 'Retry Fund',
        vault_type: 'STANDARD',
        status: 'ACTIVE',
        ledger_account_id: 'l',
        created_at: '',
      });

    renderCreate();
    fireEvent.changeText(screen.getByPlaceholderText(/Emergency fund/), 'Retry Fund');

    // First tap — fails
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));
    await waitFor(() => expect(screen.getByText('Try again.')).toBeTruthy());

    const firstKey = mockMutateAsync.mock.calls[0][0].idempotencyKey;

    // Second tap — should use the SAME key
    fireEvent.press(screen.getByRole('button', { name: /Create vault/ }));
    await waitFor(() => expect(mockMutateAsync).toHaveBeenCalledTimes(2));

    const secondKey = mockMutateAsync.mock.calls[1][0].idempotencyKey;
    expect(firstKey).toEqual(secondKey);
  });
});
