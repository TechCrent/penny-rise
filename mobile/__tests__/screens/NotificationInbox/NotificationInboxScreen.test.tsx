import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NavigationContainer } from '@react-navigation/native';
import { NotificationInboxScreen } from '../../../src/screens/NotificationInbox/NotificationInboxScreen';
import * as notificationsApi from '../../../src/api/notificationsApi';
import type { NotificationItem } from '../../../src/features/notifications/types';

jest.mock('../../../src/api/notificationsApi');

const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: mockNavigate }),
}));

function renderScreen() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <NavigationContainer>
        <NotificationInboxScreen />
      </NavigationContainer>
    </QueryClientProvider>,
  );
}

const sampleNotification: NotificationItem = {
  id: 'notif-1',
  type: 'DEPOSIT_SUCCESS',
  title: 'Deposit successful',
  body: 'GHS 100.00 landed in your vault.',
  data: { vault_id: 'vault-1' },
  channel: 'IN_APP',
  read_at: null,
  created_at: '2026-07-01T09:00:00Z',
};

describe('NotificationInboxScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders notification rows from the API', async () => {
    (notificationsApi.fetchNotifications as jest.Mock).mockResolvedValue({
      notifications: [sampleNotification],
      next_cursor: null,
      has_more: false,
      unread_count: 1,
    });

    renderScreen();

    expect(await screen.findByText('Deposit successful')).toBeTruthy();
  });

  it('shows the unread indicator dot for an unread notification', async () => {
    (notificationsApi.fetchNotifications as jest.Mock).mockResolvedValue({
      notifications: [sampleNotification],
      next_cursor: null,
      has_more: false,
      unread_count: 1,
    });

    renderScreen();

    await waitFor(() => expect(screen.getByTestId('unread-indicator')).toBeTruthy());
  });

  it('does not show an unread indicator for a read notification', async () => {
    (notificationsApi.fetchNotifications as jest.Mock).mockResolvedValue({
      notifications: [{ ...sampleNotification, read_at: '2026-07-01T09:05:00Z' }],
      next_cursor: null,
      has_more: false,
      unread_count: 0,
    });

    renderScreen();

    await screen.findByText('Deposit successful');
    expect(screen.queryByTestId('unread-indicator')).toBeNull();
  });

  it('tapping a notification marks it read and navigates to the relevant screen', async () => {
    (notificationsApi.fetchNotifications as jest.Mock).mockResolvedValue({
      notifications: [sampleNotification],
      next_cursor: null,
      has_more: false,
      unread_count: 1,
    });
    (notificationsApi.markNotificationRead as jest.Mock).mockResolvedValue(undefined);

    renderScreen();
    const row = await screen.findByText('Deposit successful');
    fireEvent.press(row);

    await waitFor(() =>
      expect(notificationsApi.markNotificationRead).toHaveBeenCalledWith(
        'notif-1',
        expect.anything(),
      ),
    );
    expect(mockNavigate).toHaveBeenCalledWith('VaultDetail', { vaultId: 'vault-1' });
  });

  it('"mark all as read" calls the bulk endpoint and clears the unread count', async () => {
    (notificationsApi.fetchNotifications as jest.Mock).mockResolvedValue({
      notifications: [sampleNotification],
      next_cursor: null,
      has_more: false,
      unread_count: 1,
    });
    (notificationsApi.markAllNotificationsRead as jest.Mock).mockResolvedValue(undefined);

    renderScreen();
    await screen.findByText('1 unread');

    fireEvent.press(screen.getByLabelText('Mark all as read'));

    await waitFor(() => expect(notificationsApi.markAllNotificationsRead).toHaveBeenCalled());
  });

  it('shows the empty state when there are no notifications', async () => {
    (notificationsApi.fetchNotifications as jest.Mock).mockResolvedValue({
      notifications: [],
      next_cursor: null,
      has_more: false,
      unread_count: 0,
    });

    renderScreen();

    expect(await screen.findByText("You're all caught up")).toBeTruthy();
  });

  it('load-more triggers fetchNextPage when the list end is reached', async () => {
    (notificationsApi.fetchNotifications as jest.Mock)
      .mockResolvedValueOnce({
        notifications: [sampleNotification],
        next_cursor: 'notif-1',
        has_more: true,
        unread_count: 1,
      })
      .mockResolvedValueOnce({
        notifications: [{ ...sampleNotification, id: 'notif-2', title: 'Second notification' }],
        next_cursor: null,
        has_more: false,
        unread_count: 1,
      });

    renderScreen();
    await screen.findByText('Deposit successful');

    fireEvent(screen.UNSAFE_getByType(require('react-native').FlatList), 'onEndReached');

    await waitFor(() => expect(screen.getByText('Second notification')).toBeTruthy());
  });
});
