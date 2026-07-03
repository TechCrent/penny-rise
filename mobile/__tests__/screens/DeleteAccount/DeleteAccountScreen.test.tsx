import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NavigationContainer } from '@react-navigation/native';
import { DeleteAccountScreen } from '../../../src/screens/DeleteAccount/DeleteAccountScreen';
import * as deletionApi from '../../../src/api/deletionApi';

jest.mock('../../../src/api/deletionApi');

const mockGoBack = jest.fn();
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ goBack: mockGoBack, navigate: jest.fn() }),
}));

function renderScreen() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <NavigationContainer>
        <DeleteAccountScreen />
      </NavigationContainer>
    </QueryClientProvider>,
  );
}

describe('DeleteAccountScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('pre-submission state shows blockers fetched from the API', async () => {
    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockResolvedValue(null);
    (deletionApi.fetchDeletionBlockers as jest.Mock).mockResolvedValue([
      {
        type: 'OPEN_SUSU_GROUP',
        description: "Susu group 'Legon Roommates' has an active rotation",
      },
    ]);

    renderScreen();

    await waitFor(() => expect(screen.getByTestId('blockers-list')).toBeTruthy());
    expect(screen.getByText(/Legon Roommates/)).toBeTruthy();
  });

  it('pre-submission state with no blockers renders no blockers box', async () => {
    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockResolvedValue(null);
    (deletionApi.fetchDeletionBlockers as jest.Mock).mockResolvedValue([]);

    renderScreen();

    await screen.findByLabelText('Confirm delete account');
    expect(screen.queryByTestId('blockers-list')).toBeNull();
  });

  it('pre-submission state treats a blockers-fetch failure as no blockers, not a screen error', async () => {
    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockResolvedValue(null);
    (deletionApi.fetchDeletionBlockers as jest.Mock).mockRejectedValue(
      new Error('not implemented'),
    );

    renderScreen();

    await screen.findByLabelText('Confirm delete account');
    expect(screen.queryByTestId('blockers-list')).toBeNull();
    expect(screen.queryByTestId('blockers-loading')).toBeNull();
  });

  it('confirm button is disabled until the acknowledgement checkbox is checked', async () => {
    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockResolvedValue(null);
    (deletionApi.fetchDeletionBlockers as jest.Mock).mockResolvedValue([]);

    renderScreen();

    const confirmButton = await screen.findByLabelText('Confirm delete account');
    expect(confirmButton.props.accessibilityState.disabled).toBe(true);

    fireEvent.press(screen.getByTestId('ack-checkbox'));

    await waitFor(() => {
      const updatedButton = screen.getByLabelText('Confirm delete account');
      expect(updatedButton.props.accessibilityState.disabled).toBe(false);
    });
  });

  it('submitting the request calls POST and the screen re-renders into cool-off', async () => {
    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockResolvedValue(null);
    (deletionApi.fetchDeletionBlockers as jest.Mock).mockResolvedValue([]);
    (deletionApi.submitDeletionRequest as jest.Mock).mockResolvedValue({
      id: 'del-1',
      status: 'PENDING',
      submitted_at: '2026-07-01T00:00:00Z',
      scheduled_completion_at: '2026-07-31T00:00:00Z',
    });

    renderScreen();

    fireEvent.press(await screen.findByTestId('ack-checkbox'));
    fireEvent.press(screen.getByLabelText('Confirm delete account'));

    await waitFor(() => expect(deletionApi.submitDeletionRequest).toHaveBeenCalled());
    expect(await screen.findByLabelText('Cancel deletion request')).toBeTruthy();
  });

  it('cool-off state shows the scheduled completion date and a Cancel Request button', async () => {
    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockResolvedValue({
      id: 'del-1',
      status: 'PENDING',
      submitted_at: '2026-07-01T00:00:00Z',
      scheduled_completion_at: '2026-07-31T00:00:00Z',
    });

    renderScreen();

    expect(await screen.findByLabelText('Cancel deletion request')).toBeTruthy();
    expect(screen.getByText(/31 July 2026/)).toBeTruthy();
  });

  it('cancel flow calls DELETE and navigates back after the confirmation alert', async () => {
    const { Alert } = require('react-native');
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation((...args: any[]) => {
      const buttons = args[2];
      buttons?.[0]?.onPress?.();
    });

    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockResolvedValue({
      id: 'del-1',
      status: 'PENDING',
      submitted_at: '2026-07-01T00:00:00Z',
      scheduled_completion_at: '2026-07-31T00:00:00Z',
    });
    (deletionApi.cancelDeletionRequest as jest.Mock).mockResolvedValue(undefined);

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Cancel deletion request'));

    await waitFor(() => expect(deletionApi.cancelDeletionRequest).toHaveBeenCalled());
    expect(alertSpy).toHaveBeenCalledWith(
      'Deletion request cancelled',
      'Your account is no longer scheduled for deletion.',
      expect.anything(),
    );
    expect(mockGoBack).toHaveBeenCalled();

    alertSpy.mockRestore();
  });

  it('opens directly in cool-off state if a request is already active on screen mount', async () => {
    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockResolvedValue({
      id: 'del-1',
      status: 'PENDING',
      submitted_at: '2026-06-15T00:00:00Z',
      scheduled_completion_at: '2026-07-15T00:00:00Z',
    });

    renderScreen();

    // Never shows the pre-submission checkbox/CTA at all — goes straight to cool-off.
    expect(await screen.findByLabelText('Cancel deletion request')).toBeTruthy();
    expect(screen.queryByLabelText('Confirm delete account')).toBeNull();
  });

  it('shows a loading state while the initial active-request check is in flight', () => {
    (deletionApi.fetchActiveDeletionRequest as jest.Mock).mockImplementation(
      () => new Promise(() => {}), // never resolves — keeps us in LOADING
    );

    renderScreen();

    expect(screen.getByTestId('delete-account-loading')).toBeTruthy();
  });
});
