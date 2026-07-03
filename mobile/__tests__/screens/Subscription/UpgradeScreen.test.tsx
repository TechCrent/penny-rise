import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { UpgradeScreen } from '../../../src/screens/Subscription/UpgradeScreen';

jest.mock('expo-web-browser', () => ({
  openBrowserAsync: jest.fn(),
}));
jest.mock('../../../src/api/subscriptionApi');
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: jest.fn(), navigate: jest.fn() }),
}));

const WebBrowser = require('expo-web-browser');
const subscriptionApi = require('../../../src/api/subscriptionApi');

function renderScreen() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <UpgradeScreen />
    </QueryClientProvider>,
  );
}

const freeStatus = {
  tier: 'FREE',
  started_at: '2026-01-01T00:00:00Z',
  ends_at: null,
  source: 'SYSTEM',
  free_transfers_remaining: 5,
};

const premiumStatus = {
  tier: 'PREMIUM',
  started_at: '2026-06-01T00:00:00Z',
  ends_at: null,
  source: 'PAYSTACK_SUB',
  free_transfers_remaining: 20,
};

describe('UpgradeScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('shows PREMIUM benefits and price when not already premium', async () => {
    subscriptionApi.fetchSubscriptionStatus.mockResolvedValue(freeStatus);

    renderScreen();

    expect(await screen.findByText('Stash Premium')).toBeTruthy();
    expect(screen.getByText('GHS 15 / month')).toBeTruthy();
    expect(screen.getByLabelText('Upgrade to Premium')).toBeTruthy();
  });

  it('shows "already on Premium" state with no upgrade button', async () => {
    subscriptionApi.fetchSubscriptionStatus.mockResolvedValue(premiumStatus);

    renderScreen();

    expect(await screen.findByTestId('already-premium-message')).toBeTruthy();
    expect(screen.queryByLabelText('Upgrade to Premium')).toBeNull();
  });

  it('Paystack success path: initiates, opens browser, confirms, shows success', async () => {
    subscriptionApi.fetchSubscriptionStatus.mockResolvedValue(freeStatus);
    subscriptionApi.initiateUpgrade.mockResolvedValue({
      authorization_url: 'https://paystack.com/checkout/xyz',
      reference: 'ref-123',
    });
    WebBrowser.openBrowserAsync.mockResolvedValue({ type: 'dismiss' });
    subscriptionApi.confirmUpgrade.mockResolvedValue({
      tier: 'PREMIUM',
      started_at: '2026-07-01T00:00:00Z',
    });

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Upgrade to Premium'));

    await waitFor(() => expect(subscriptionApi.initiateUpgrade).toHaveBeenCalled());
    await waitFor(() =>
      expect(WebBrowser.openBrowserAsync).toHaveBeenCalledWith(
        'https://paystack.com/checkout/xyz',
        expect.any(Object),
      ),
    );
    await waitFor(() => expect(subscriptionApi.confirmUpgrade).toHaveBeenCalledWith('ref-123'));

    expect(await screen.findByTestId('upgrade-success')).toBeTruthy();
  });

  it('Paystack failure (cancelled in browser) shows failure screen with retry and back', async () => {
    subscriptionApi.fetchSubscriptionStatus.mockResolvedValue(freeStatus);
    subscriptionApi.initiateUpgrade.mockResolvedValue({
      authorization_url: 'https://paystack.com/checkout/xyz',
      reference: 'ref-123',
    });
    WebBrowser.openBrowserAsync.mockResolvedValue({ type: 'cancel' });

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Upgrade to Premium'));

    expect(await screen.findByTestId('upgrade-error-title')).toBeTruthy();
    expect(screen.getByLabelText('Retry upgrade')).toBeTruthy();
    expect(screen.getByLabelText('Go back')).toBeTruthy();
    expect(subscriptionApi.confirmUpgrade).not.toHaveBeenCalled();
  });

  it('network error during initiate shows the network error state, not a silent failure', async () => {
    subscriptionApi.fetchSubscriptionStatus.mockResolvedValue(freeStatus);
    subscriptionApi.initiateUpgrade.mockRejectedValue(new Error('network down'));

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Upgrade to Premium'));

    expect(await screen.findByText("Couldn't reach Stash")).toBeTruthy();
  });

  it('server 5xx error during confirm shows the network error state, distinct from a Paystack failure', async () => {
    subscriptionApi.fetchSubscriptionStatus.mockResolvedValue(freeStatus);
    subscriptionApi.initiateUpgrade.mockResolvedValue({
      authorization_url: 'https://paystack.com/checkout/xyz',
      reference: 'ref-123',
    });
    WebBrowser.openBrowserAsync.mockResolvedValue({ type: 'dismiss' });
    const axiosError: any = new Error('server error');
    axiosError.isAxiosError = true; // matches axios's real isAxiosError(payload) check
    axiosError.response = { status: 500 };
    subscriptionApi.confirmUpgrade.mockRejectedValue(axiosError);

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Upgrade to Premium'));

    expect(await screen.findByText("Couldn't reach Stash")).toBeTruthy();
  });

  it('retry from a failure state returns to the benefits screen, not a dead end', async () => {
    subscriptionApi.fetchSubscriptionStatus.mockResolvedValue(freeStatus);
    subscriptionApi.initiateUpgrade.mockRejectedValue(new Error('network down'));

    renderScreen();
    fireEvent.press(await screen.findByLabelText('Upgrade to Premium'));
    await screen.findByText("Couldn't reach Stash");

    fireEvent.press(screen.getByLabelText('Retry upgrade'));

    expect(await screen.findByText('Stash Premium')).toBeTruthy();
  });
});
