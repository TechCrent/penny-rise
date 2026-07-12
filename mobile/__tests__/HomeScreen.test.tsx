import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import HomeScreen from '../src/screens/HomeScreen';
import * as vaultsApi from '../src/api/vaults';
import * as susuApiModule from '../src/api/susu';
import { AuthProvider } from '../src/auth/AuthContext';
import type { VaultListItem, VaultListResponse } from '../src/api/vaults';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../src/hooks/useKycResumability', () => ({
  useKycResumability: jest.fn(),
}));

jest.mock('../src/screens/Challenges/useChallenges', () => ({
  useChallenges: () => ({ data: [] }),
}));

let tabPressHandler: (() => void) | null = null;
const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({
    navigate: mockNavigate,
    addListener: (event: string, handler: () => void) => {
      if (event === 'tabPress') tabPressHandler = handler;
      return () => {
        tabPressHandler = null;
      };
    },
  }),
}));

jest.mock('../src/api/vaults');
jest.mock('../src/api/susu');

const wrapper = ({ children }: { children: React.ReactNode }) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <SafeAreaProvider
      initialMetrics={{
        frame: { x: 0, y: 0, width: 390, height: 844 },
        insets: { top: 0, left: 0, right: 0, bottom: 0 },
      }}
    >
      <QueryClientProvider client={queryClient}>
        <AuthProvider>{children}</AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  );
};

function mockVaultsResponse(response: VaultListResponse) {
  (vaultsApi.listVaults as jest.Mock).mockResolvedValue(response);
}

const standardVault: VaultListItem = {
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

const lockedVault: VaultListItem = {
  id: 'vault-002',
  name: 'University Fund',
  vault_type: 'LOCKED',
  status: 'ACTIVE',
  ledger_account_id: 'ledger-002',
  balance_pesewas: 200_000,
  balance_cedis: '2,000.00',
  unlock_at: '2028-09-01T00:00:00Z',
  unlock_amount: 500_000,
  unlock_condition_logic: 'AND',
  early_exit_in_progress: false,
  created_at: '2026-06-01T00:00:00Z',
};

describe('HomeScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    tabPressHandler = null;
    (susuApiModule.susuApi.listGroups as jest.Mock).mockResolvedValue([]);
    (susuApiModule.walletApi.getBalance as jest.Mock).mockResolvedValue({
      account_id: 'wallet-001',
      balance_pesewas: 50_000,
      balance_cedis: '500.00',
    });
  });

  it('renders greeting and defaults to Total state with the combined balance', async () => {
    mockVaultsResponse({
      vaults: [standardVault, lockedVault],
      total_count: 2,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });

    await waitFor(() => expect(utils.getByText(/Good (morning|afternoon|evening)/)).toBeTruthy());
    expect(utils.getByText('Total balance')).toBeTruthy();
    // 3,000.00 (vaults) + 500.00 (wallet) = 3,500.00
    await waitFor(() => expect(utils.getByText('3,500.00')).toBeTruthy());
  });

  it('shows the segmented switcher with Total active by default', async () => {
    mockVaultsResponse({ vaults: [], total_count: 0, balance_unavailable_count: 0 });

    const utils = render(<HomeScreen />, { wrapper });

    await waitFor(() => expect(utils.getByTestId('home-state-switcher')).toBeTruthy());
    expect(utils.getByText('Total')).toBeTruthy();
    expect(utils.getByText('Savings')).toBeTruthy();
    expect(utils.getByText('Wallet')).toBeTruthy();
  });

  it('switching to Savings state shows the vault list', async () => {
    mockVaultsResponse({
      vaults: [standardVault, lockedVault],
      total_count: 2,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText('Savings')).toBeTruthy());

    fireEvent.press(utils.getByText('Savings'));

    await waitFor(() => expect(utils.getByText('Savings total')).toBeTruthy());
    expect(utils.getByText('Emergency Fund')).toBeTruthy();
    expect(utils.getByText('University Fund')).toBeTruthy();
    expect(utils.getByText('Vault activity')).toBeTruthy();
  });

  it('switching to Wallet state shows wallet balance and susu section', async () => {
    mockVaultsResponse({ vaults: [], total_count: 0, balance_unavailable_count: 0 });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText('Wallet')).toBeTruthy());

    fireEvent.press(utils.getByText('Wallet'));

    await waitFor(() => expect(utils.getByText('Wallet balance')).toBeTruthy());
    expect(utils.getByText('500.00')).toBeTruthy();
    expect(utils.getByText('Your susus')).toBeTruthy();
    expect(utils.getByText('Wallet activity')).toBeTruthy();
  });

  it('Total state: Deposit opens the account picker when vaults exist', async () => {
    mockVaultsResponse({
      vaults: [standardVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByLabelText('Deposit')).toBeTruthy());

    fireEvent.press(utils.getByLabelText('Deposit'));
    expect(mockNavigate).toHaveBeenCalledWith('AccountPicker', { mode: 'DEPOSIT' });
  });

  it('Total state: Deposit goes straight to CreateVault when there are no vaults', async () => {
    mockVaultsResponse({ vaults: [], total_count: 0, balance_unavailable_count: 0 });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByLabelText('Deposit')).toBeTruthy());

    fireEvent.press(utils.getByLabelText('Deposit'));
    expect(mockNavigate).toHaveBeenCalledWith('CreateVault');
  });

  it('Total state: Withdraw opens the account picker when vaults exist', async () => {
    mockVaultsResponse({
      vaults: [standardVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByLabelText('Withdraw')).toBeTruthy());

    fireEvent.press(utils.getByLabelText('Withdraw'));
    expect(mockNavigate).toHaveBeenCalledWith('AccountPicker', { mode: 'WITHDRAW' });
  });

  it('Total state: Send navigates directly to the recipient picker', async () => {
    mockVaultsResponse({ vaults: [], total_count: 0, balance_unavailable_count: 0 });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByLabelText('Send')).toBeTruthy());

    fireEvent.press(utils.getByLabelText('Send'));
    expect(mockNavigate).toHaveBeenCalledWith('RecipientPicker');
  });

  it('Total state: portfolio summary shows vault and susu counts', async () => {
    mockVaultsResponse({
      vaults: [standardVault, lockedVault],
      total_count: 2,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });

    await waitFor(() => expect(utils.getByTestId('portfolio-summary')).toBeTruthy());
    expect(utils.getByText('2')).toBeTruthy();
  });

  it('Savings state: tapping a vault card navigates to VaultDetail', async () => {
    mockVaultsResponse({
      vaults: [standardVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    fireEvent.press(await utils.findByText('Savings'));
    fireEvent.press(await utils.findByText('Emergency Fund'));

    expect(mockNavigate).toHaveBeenCalledWith('VaultDetail', { vaultId: 'vault-001' });
  });

  it('Savings state: "See all" navigates to VaultList', async () => {
    mockVaultsResponse({
      vaults: [standardVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    fireEvent.press(await utils.findByText('Savings'));
    fireEvent.press((await utils.findAllByText('See all'))[0]);

    expect(mockNavigate).toHaveBeenCalledWith('VaultList');
  });

  it('pressing the Home tab while on Savings resets to Total state', async () => {
    mockVaultsResponse({
      vaults: [standardVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    fireEvent.press(await utils.findByText('Savings'));
    await waitFor(() => expect(utils.getByText('Savings total')).toBeTruthy());

    expect(tabPressHandler).not.toBeNull();
    tabPressHandler?.();

    await waitFor(() => expect(utils.getByText('Total balance')).toBeTruthy());
  });
});
