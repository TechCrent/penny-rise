import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { DowngradeScreen } from '../../../src/screens/Subscription/DowngradeScreen';

jest.mock('../../../src/api/subscriptionApi');

const mockGoBack = jest.fn();
jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ goBack: mockGoBack }),
}));

const subscriptionApi = require('../../../src/api/subscriptionApi');

function renderScreen() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <DowngradeScreen />
    </QueryClientProvider>,
  );
}

const previewWithImpact = {
  vaults_to_be_frozen: [
    { vault_id: 'v-1', vault_name: 'Emergency Fund', vault_type: 'STANDARD' as const },
    { vault_id: 'v-2', vault_name: 'Laptop Fund', vault_type: 'LOCKED' as const },
  ],
  susu_groups_to_be_frozen: [{ susu_group_id: 'g-1', susu_group_name: 'Circle A' }],
  committed: false,
};

const previewNoImpact = {
  vaults_to_be_frozen: [],
  susu_groups_to_be_frozen: [],
  committed: false,
};

describe('DowngradeScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('impact preview lists frozen vault names, susu group names, and the exact warning copy', async () => {
    subscriptionApi.fetchDowngradePreview.mockResolvedValue(previewWithImpact);

    renderScreen();

    expect(await screen.findByText('Emergency Fund')).toBeTruthy();
    expect(screen.getByText('Laptop Fund')).toBeTruthy();
    expect(screen.getByText('Circle A')).toBeTruthy();
    expect(screen.getByText(/Your locked vault funds remain safe/)).toBeTruthy();
  });

  it('impact preview with no frozen resources shows a simple confirmation, no impact list', async () => {
    subscriptionApi.fetchDowngradePreview.mockResolvedValue(previewNoImpact);

    renderScreen();

    expect(await screen.findByTestId('no-impact-message')).toBeTruthy();
    expect(screen.queryByTestId(/frozen-vault-/)).toBeNull();
    expect(screen.queryByTestId(/frozen-susu-/)).toBeNull();
  });

  it('confirm button is disabled until the acknowledgement checkbox is checked', async () => {
    subscriptionApi.fetchDowngradePreview.mockResolvedValue(previewWithImpact);

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Continue to confirmation'));

    const confirmButton = await screen.findByLabelText('Confirm downgrade');
    expect(confirmButton.props.accessibilityState.disabled).toBe(true);

    fireEvent.press(screen.getByTestId('downgrade-ack-checkbox'));

    await waitFor(() =>
      expect(screen.getByLabelText('Confirm downgrade').props.accessibilityState.disabled).toBe(
        false,
      ),
    );
  });

  it('cancel at step 1 (preview) returns to the previous screen with no commit call made', async () => {
    subscriptionApi.fetchDowngradePreview.mockResolvedValue(previewWithImpact);

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Cancel downgrade'));

    expect(mockGoBack).toHaveBeenCalled();
    expect(subscriptionApi.commitDowngrade).not.toHaveBeenCalled();
  });

  it('cancel at step 2 (confirm) also returns to the previous screen with no commit call made', async () => {
    subscriptionApi.fetchDowngradePreview.mockResolvedValue(previewWithImpact);

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Continue to confirmation'));
    fireEvent.press(await screen.findByLabelText('Cancel downgrade'));

    expect(mockGoBack).toHaveBeenCalled();
    expect(subscriptionApi.commitDowngrade).not.toHaveBeenCalled();
  });

  it('confirming the downgrade calls the commit endpoint and shows the success state', async () => {
    subscriptionApi.fetchDowngradePreview.mockResolvedValue(previewWithImpact);
    subscriptionApi.commitDowngrade.mockResolvedValue({ ...previewWithImpact, committed: true });

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Continue to confirmation'));
    fireEvent.press(await screen.findByTestId('downgrade-ack-checkbox'));
    fireEvent.press(screen.getByLabelText('Confirm downgrade'));

    await waitFor(() => expect(subscriptionApi.commitDowngrade).toHaveBeenCalled());
    expect(await screen.findByTestId('downgrade-success')).toBeTruthy();
  });

  it('a failed commit shows the error state with a retry affordance, not a silent failure', async () => {
    subscriptionApi.fetchDowngradePreview.mockResolvedValue(previewWithImpact);
    subscriptionApi.commitDowngrade.mockRejectedValue(new Error('network down'));

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Continue to confirmation'));
    fireEvent.press(await screen.findByTestId('downgrade-ack-checkbox'));
    fireEvent.press(screen.getByLabelText('Confirm downgrade'));

    expect(await screen.findByTestId('downgrade-error')).toBeTruthy();
    expect(screen.getByLabelText('Retry downgrade')).toBeTruthy();
  });

  it('cancel and continue buttons render with equal visual weight at step 1 (no dark pattern)', async () => {
    subscriptionApi.fetchDowngradePreview.mockResolvedValue(previewWithImpact);

    renderScreen();

    const cancelButton = await screen.findByLabelText('Cancel downgrade');
    const continueButton = screen.getByLabelText('Continue to confirmation');

    expect(cancelButton.props.style).toEqual(expect.objectContaining({ flex: 1 }));
    expect(continueButton.props.style).toEqual(expect.objectContaining({ flex: 1 }));
  });
});
