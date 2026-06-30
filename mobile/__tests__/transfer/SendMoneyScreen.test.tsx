import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { SendMoneyScreen } from '../../src/screens/transfer/SendMoneyScreen';
import * as api from '../../src/api/transfers';
import * as balanceHook from '../../src/hooks/useWalletBalance';

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ replace: jest.fn(), goBack: jest.fn() }),
  useRoute: () => ({
    params: {
      recipient: { id: 'u1', displayName: 'Kofi Mensah', email: 'kofi@example.com' },
    },
  }),
}));

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

function mockQuota(freeRemaining: number) {
  jest.spyOn(api.transferApi, 'getQuota').mockResolvedValue({
    freeTransfersUsed: 5 - freeRemaining,
    freeTransfersRemaining: freeRemaining,
    freeQuotaLimit: 5,
    nextTransferIsFree: freeRemaining > 0,
    feeIfTransferNowPesewas: freeRemaining > 0 ? 0 : 200,
    feeIfTransferNowCedis: freeRemaining > 0 ? '0.00' : '2.00',
  });
}

const mockTransferResult = {
  id: 't1',
  transactionReference: 'STSH-202606-TRF001',
  amount: 5000,
  amountCedis: '50.00',
  feeAmount: 0,
  feeAmountCedis: '0.00',
  totalDebited: 5000,
  totalDebitedCedis: '50.00',
  freeTransfersRemaining: 2,
  recipientUserId: 'u1',
  status: 'COMPLETED',
  completedAt: '2026-06-30T10:00:00Z',
};

describe('SendMoneyScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockBalance(100_000);
    mockQuota(3);
  });

  afterEach(() => jest.restoreAllMocks());

  it('renders recipient name from params', () => {
    render(<SendMoneyScreen />);
    expect(screen.getByTestId('recipient-name')).toBeTruthy();
    expect(screen.getByText('Kofi Mensah')).toBeTruthy();
  });

  it('shows wallet balance', () => {
    render(<SendMoneyScreen />);
    expect(screen.getByTestId('wallet-balance')).toBeTruthy();
    expect(screen.getByText(/1000\.00/)).toBeTruthy();
  });

  it('shows free transfer label when quota has remaining transfers', async () => {
    render(<SendMoneyScreen />);
    await waitFor(() => {
      expect(screen.getByTestId('free-transfer-label')).toBeTruthy();
    });
  });

  it('shows fee row when no free transfers remain', async () => {
    mockQuota(0);
    render(<SendMoneyScreen />);
    await waitFor(() => {
      expect(screen.getByTestId('fee-row')).toBeTruthy();
      expect(screen.getByText(/2\.00/)).toBeTruthy();
    });
  });

  it('submit button is disabled when amount is empty', () => {
    render(<SendMoneyScreen />);
    expect(screen.getByTestId('submit-btn').props.accessibilityState?.disabled).toBeTruthy();
  });

  it('shows insufficient balance warning when amount exceeds balance', async () => {
    render(<SendMoneyScreen />);
    fireEvent.changeText(screen.getByTestId('amount-input'), '2000');
    await waitFor(() => {
      expect(screen.getByTestId('insufficient-balance')).toBeTruthy();
    });
  });

  it('calls transferApi.send with correct params', async () => {
    jest.spyOn(api.transferApi, 'send').mockResolvedValue(mockTransferResult);
    render(<SendMoneyScreen />);
    fireEvent.changeText(screen.getByTestId('amount-input'), '50');
    await waitFor(() => {
      expect(screen.getByTestId('submit-btn').props.accessibilityState?.disabled).toBeFalsy();
    });
    fireEvent.press(screen.getByTestId('submit-btn'));
    await waitFor(() => {
      expect(api.transferApi.send).toHaveBeenCalledWith(
        expect.objectContaining({
          recipientId: 'u1',
          amountPesewas: 5000,
          idempotencyKey: expect.any(String),
        }),
      );
    });
  });

  it('navigates to TransferSuccess after successful send', async () => {
    const replace = jest.fn();
    jest
      .spyOn(require('@react-navigation/native'), 'useNavigation')
      .mockReturnValue({ replace, goBack: jest.fn() });
    jest.spyOn(api.transferApi, 'send').mockResolvedValue(mockTransferResult);
    render(<SendMoneyScreen />);
    fireEvent.changeText(screen.getByTestId('amount-input'), '50');
    await waitFor(() => {
      expect(screen.getByTestId('submit-btn').props.accessibilityState?.disabled).toBeFalsy();
    });
    fireEvent.press(screen.getByTestId('submit-btn'));
    await waitFor(() => {
      expect(replace).toHaveBeenCalledWith('TransferSuccess', {
        result: mockTransferResult,
        recipientName: 'Kofi Mensah',
      });
    });
  });

  it('shows generic error banner on network failure', async () => {
    jest.spyOn(api.transferApi, 'send').mockRejectedValue({ message: 'Network Error' });
    render(<SendMoneyScreen />);
    fireEvent.changeText(screen.getByTestId('amount-input'), '50');
    await waitFor(() => {
      expect(screen.getByTestId('submit-btn').props.accessibilityState?.disabled).toBeFalsy();
    });
    fireEvent.press(screen.getByTestId('submit-btn'));
    await waitFor(() => {
      expect(screen.getByTestId('submit-error')).toBeTruthy();
      expect(screen.getByText('Transfer failed. Please try again.')).toBeTruthy();
    });
  });

  it('shows specific error for TRANSFER_INSUFFICIENT_BALANCE', async () => {
    jest
      .spyOn(api.transferApi, 'send')
      .mockRejectedValue({ code: 'TRANSFER_INSUFFICIENT_BALANCE' });
    render(<SendMoneyScreen />);
    fireEvent.changeText(screen.getByTestId('amount-input'), '50');
    await waitFor(() => {
      expect(screen.getByTestId('submit-btn').props.accessibilityState?.disabled).toBeFalsy();
    });
    fireEvent.press(screen.getByTestId('submit-btn'));
    await waitFor(() => {
      expect(screen.getByTestId('submit-error')).toBeTruthy();
      expect(screen.getByText('Insufficient balance for this transfer.')).toBeTruthy();
    });
  });
});
