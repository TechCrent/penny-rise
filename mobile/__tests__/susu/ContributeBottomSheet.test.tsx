import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import { ContributeBottomSheet } from '../../src/components/susu/ContributeBottomSheet';
import * as balanceHook from '../../src/hooks/useWalletBalance';
import * as susuApiModule from '../../src/api/susu';

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: jest.fn() }),
}));

const mockGroup = {
  id: 'g1',
  name: 'Akua Circle',
  status: 'ACTIVE' as const,
  organiser_user_id: 'user-1',
  is_caller_organiser: false,
  contribution_amount: 20000,
  contribution_amount_cedis: '200.00',
  frequency: 'MONTHLY' as const,
  target_member_count: 5,
  join_code: 'STSH1234',
  start_date: '2024-01-01',
  created_at: '2024-01-01T00:00:00Z',
  current_round: {
    id: 'round-1',
    round_number: 1,
    total_rounds: 5,
    status: 'COLLECTING' as const,
    recipient_user_id: 'user-2',
    recipient_display_name: 'Kofi',
    scheduled_collection_at: null,
    expected_pot_amount: 100000,
    expected_pot_amount_cedis: '1000.00',
    actual_pot_amount: null,
    contributions: [],
  },
  members: [],
  caller_membership: {
    id: 'mem-1',
    rotation_position: 2,
    status: 'ACTIVE',
    joined_at: '2024-01-01T00:00:00Z',
  },
};

const onClose = jest.fn();
const onSuccess = jest.fn();

function mockBalance(pesewas: number) {
  jest.spyOn(balanceHook, 'useWalletBalance').mockReturnValue({
    balance: {
      accountId: 'acc-1',
      balancePesewas: pesewas,
      balanceCedis: (pesewas / 100).toFixed(2),
    },
    loading: false,
    error: null,
    fetch: jest.fn(),
  });
}

describe('ContributeBottomSheet', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('renders contribution amount and wallet balance', () => {
    mockBalance(50_000);
    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    expect(screen.getByTestId('contribution-amount')).toBeTruthy();
    expect(screen.getByTestId('wallet-balance')).toBeTruthy();
    expect(screen.getAllByText(/200\.00/).length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText(/500\.00/)).toBeTruthy();
  });

  it('calls onClose when close button tapped', () => {
    mockBalance(50_000);
    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    fireEvent.press(screen.getByTestId('close-btn'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('calls payContribution on confirm and shows success state', async () => {
    mockBalance(50_000);
    jest
      .spyOn(susuApiModule.susuApi, 'payContribution')
      .mockResolvedValue({ id: 'c1', status: 'PAID', round_fully_collected: false });

    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    fireEvent.press(screen.getByTestId('confirm-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('success-state')).toBeTruthy();
    });
    expect(susuApiModule.susuApi.payContribution).toHaveBeenCalledWith(
      'round-1',
      expect.any(String),
    );
  });

  it('shows error text on payment failure', async () => {
    mockBalance(50_000);
    jest
      .spyOn(susuApiModule.susuApi, 'payContribution')
      .mockRejectedValue({ message: 'Insufficient funds' });

    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    fireEvent.press(screen.getByTestId('confirm-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('submit-error')).toBeTruthy();
      expect(screen.getByText('Insufficient funds')).toBeTruthy();
    });
  });

  it('same idempotency key used on retry', async () => {
    mockBalance(50_000);
    const capturedKeys: string[] = [];
    jest
      .spyOn(susuApiModule.susuApi, 'payContribution')
      .mockImplementation(async (_roundId, key) => {
        capturedKeys.push(key);
        throw new Error('Timeout');
      });

    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );

    fireEvent.press(screen.getByTestId('confirm-btn'));
    await waitFor(() => expect(screen.getByTestId('submit-error')).toBeTruthy());

    fireEvent.press(screen.getByTestId('confirm-btn'));
    await waitFor(() => expect(capturedKeys.length).toBe(2));

    expect(capturedKeys[0]).toBe(capturedKeys[1]);
    expect(capturedKeys[0]).not.toBe('');
  });

  it('confirm button is disabled when balance insufficient', () => {
    mockBalance(5_000);
    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    const btn = screen.getByTestId('confirm-btn');
    expect(btn.props.accessibilityState?.disabled).toBeTruthy();
  });

  it('does not call payContribution when balance is insufficient', async () => {
    mockBalance(5_000);
    const spy = jest.spyOn(susuApiModule.susuApi, 'payContribution');

    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    fireEvent.press(screen.getByTestId('confirm-btn'));

    await waitFor(() => {
      expect(spy).not.toHaveBeenCalled();
    });
  });

  it('shows loading indicator while fetching balance', () => {
    jest.spyOn(balanceHook, 'useWalletBalance').mockReturnValue({
      balance: null,
      loading: true,
      error: null,
      fetch: jest.fn(),
    });
    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    expect(screen.getByTestId('balance-loading')).toBeTruthy();
  });

  it('auto-closes after 1200 ms on success', async () => {
    mockBalance(50_000);
    jest
      .spyOn(susuApiModule.susuApi, 'payContribution')
      .mockResolvedValue({ id: 'c1', status: 'PAID', round_fully_collected: false });

    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    fireEvent.press(screen.getByTestId('confirm-btn'));

    await waitFor(() => expect(screen.getByTestId('success-state')).toBeTruthy());
    act(() => {
      jest.advanceTimersByTime(1200);
    });
    expect(onSuccess).toHaveBeenCalled();
  });

  it('shows sheet title', () => {
    mockBalance(50_000);
    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    expect(screen.getByText('Pay contribution')).toBeTruthy();
  });

  it('shows (low) label when balance is insufficient', () => {
    mockBalance(5_000);
    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    expect(screen.getByText(/ \(low\)/)).toBeTruthy();
  });

  it('confirm button shows spinner while confirming', async () => {
    mockBalance(50_000);
    let resolvePayment!: () => void;
    jest.spyOn(susuApiModule.susuApi, 'payContribution').mockImplementation(
      () =>
        new Promise(resolve => {
          resolvePayment = () =>
            resolve({ id: 'c1', status: 'PAID', round_fully_collected: false });
        }),
    );

    render(
      <ContributeBottomSheet visible group={mockGroup} onClose={onClose} onSuccess={onSuccess} />,
    );
    fireEvent.press(screen.getByTestId('confirm-btn'));

    await waitFor(() => {
      expect(screen.queryByText(/Pay GHS/)).toBeNull();
    });

    await act(async () => {
      resolvePayment();
    });
  });
});
