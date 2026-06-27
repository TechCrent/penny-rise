import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { RefreshControl } from 'react-native';
import { NavigationContainer } from '@react-navigation/native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import HomeScreen from '../src/screens/HomeScreen';
import * as vaultsApi from '../src/api/vaults';
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

const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: mockNavigate }),
}));

jest.mock('../src/api/vaults');

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
        <AuthProvider>
          <NavigationContainer>{children}</NavigationContainer>
        </AuthProvider>
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

const earlyExitVault: VaultListItem = {
  ...lockedVault,
  id: 'vault-003',
  name: 'Car Fund',
  status: 'EARLY_EXIT_PENDING',
  early_exit_in_progress: true,
};

describe('HomeScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders greeting and vault cards when data is loaded', async () => {
    mockVaultsResponse({
      vaults: [standardVault, lockedVault],
      total_count: 2,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });

    await waitFor(() => expect(utils.getByText('Emergency Fund')).toBeTruthy());
    expect(utils.getByText(/Good (morning|afternoon|evening)/)).toBeTruthy();
    expect(utils.getByText('University Fund')).toBeTruthy();
    expect(utils.getByText('1,000.00')).toBeTruthy();
    expect(utils.getByText('2,000.00')).toBeTruthy();
  });

  it('shows STANDARD and LOCKED type badges correctly', async () => {
    mockVaultsResponse({
      vaults: [standardVault, lockedVault],
      total_count: 2,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });

    await waitFor(() => expect(utils.getByText('STANDARD')).toBeTruthy());
    expect(utils.getByText('LOCKED')).toBeTruthy();
  });

  it('shows EARLY EXIT pill for EARLY_EXIT_PENDING vault', async () => {
    mockVaultsResponse({
      vaults: [earlyExitVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText('EARLY EXIT')).toBeTruthy());
  });

  it('shows empty state when user has no vaults', async () => {
    mockVaultsResponse({ vaults: [], total_count: 0, balance_unavailable_count: 0 });

    const utils = render(<HomeScreen />, { wrapper });

    await waitFor(() => expect(utils.getByText('No vaults yet')).toBeTruthy());
    expect(utils.getByText('Create your first vault')).toBeTruthy();
    expect(utils.queryByText('See all')).toBeNull();
  });

  it('shows — for vaults with null balance when Payments Service unavailable', async () => {
    const unavailableVault: VaultListItem = {
      ...standardVault,
      balance_pesewas: null,
      balance_cedis: null,
    };
    mockVaultsResponse({
      vaults: [unavailableVault],
      total_count: 1,
      balance_unavailable_count: 1,
    });

    const utils = render(<HomeScreen />, { wrapper });

    await waitFor(() => expect(utils.getAllByText('—').length).toBeGreaterThanOrEqual(1));
    expect(utils.getByText(/1 balance unavailable/)).toBeTruthy();
  });

  it('shows correct total balance across all vaults', async () => {
    mockVaultsResponse({
      vaults: [standardVault, lockedVault],
      total_count: 2,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText('3,000.00')).toBeTruthy());
  });

  it('pull-to-refresh re-fetches vaults', async () => {
    mockVaultsResponse({
      vaults: [standardVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText('Emergency Fund')).toBeTruthy());
    expect(vaultsApi.listVaults).toHaveBeenCalledTimes(1);

    const refreshControl = utils.UNSAFE_getByType(RefreshControl);
    refreshControl.props.onRefresh();

    await waitFor(() => expect(vaultsApi.listVaults).toHaveBeenCalledTimes(2));
  });

  it('shows "See all" link and navigates to VaultList when vaults exist', async () => {
    mockVaultsResponse({
      vaults: [standardVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText('See all')).toBeTruthy());

    fireEvent.press(utils.getByText('See all'));
    expect(mockNavigate).toHaveBeenCalledWith('VaultList');
  });

  it('shows overflow card when more than 2 vaults exist', async () => {
    const v3: VaultListItem = { ...standardVault, id: 'vault-004', name: 'Holiday Fund' };
    mockVaultsResponse({
      vaults: [standardVault, lockedVault, v3],
      total_count: 3,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText(/\+1 more vault/)).toBeTruthy());
  });

  it('navigates to VaultDetail when a vault card is pressed', async () => {
    mockVaultsResponse({
      vaults: [standardVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText('Emergency Fund')).toBeTruthy());

    fireEvent.press(utils.getByText('Emergency Fund'));
    expect(mockNavigate).toHaveBeenCalledWith('VaultDetail', { vaultId: 'vault-001' });
  });

  it('shows unlock date label for date-based locked vault', async () => {
    mockVaultsResponse({
      vaults: [lockedVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText(/Unlocks on/)).toBeTruthy());
  });

  it('shows goal label for amount-based locked vault', async () => {
    const amountVault: VaultListItem = {
      ...lockedVault,
      id: 'vault-005',
      unlock_at: null,
      unlock_amount: 500_000,
      unlock_condition_logic: null,
    };
    mockVaultsResponse({
      vaults: [amountVault],
      total_count: 1,
      balance_unavailable_count: 0,
    });

    const utils = render(<HomeScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText(/Goal: GHS 5000\.00/)).toBeTruthy());
  });
});
