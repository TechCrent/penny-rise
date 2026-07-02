import * as Notifications from 'expo-notifications';
import { registerPushToken } from '../../../src/features/notifications/pushSetup';
import { apiClient } from '../../../src/api/client';

jest.mock('expo-notifications', () => ({
  getPermissionsAsync: jest.fn(),
  requestPermissionsAsync: jest.fn(),
  getExpoPushTokenAsync: jest.fn(),
}));

jest.mock('../../../src/api/client', () => ({
  apiClient: { post: jest.fn() },
}));

describe('registerPushToken', () => {
  beforeEach(() => jest.clearAllMocks());

  it('registers the token when permission is already granted', async () => {
    (Notifications.getPermissionsAsync as jest.Mock).mockResolvedValue({ status: 'granted' });
    (Notifications.getExpoPushTokenAsync as jest.Mock).mockResolvedValue({
      data: 'ExponentPushToken[abc123]',
    });
    (apiClient.post as jest.Mock).mockResolvedValue({});

    await registerPushToken();

    expect(apiClient.post).toHaveBeenCalledWith(
      '/api/v1/devices/register',
      expect.objectContaining({ token: 'ExponentPushToken[abc123]' }),
    );
  });

  it('requests permission when not already granted', async () => {
    (Notifications.getPermissionsAsync as jest.Mock).mockResolvedValue({ status: 'undetermined' });
    (Notifications.requestPermissionsAsync as jest.Mock).mockResolvedValue({ status: 'granted' });
    (Notifications.getExpoPushTokenAsync as jest.Mock).mockResolvedValue({
      data: 'ExponentPushToken[xyz]',
    });
    (apiClient.post as jest.Mock).mockResolvedValue({});

    await registerPushToken();

    expect(Notifications.requestPermissionsAsync).toHaveBeenCalled();
    expect(apiClient.post).toHaveBeenCalled();
  });

  it('does nothing (no error) if permission is denied', async () => {
    (Notifications.getPermissionsAsync as jest.Mock).mockResolvedValue({ status: 'denied' });
    (Notifications.requestPermissionsAsync as jest.Mock).mockResolvedValue({ status: 'denied' });

    await expect(registerPushToken()).resolves.toBeUndefined();
    expect(apiClient.post).not.toHaveBeenCalled();
  });

  it('never throws, even if the network call fails', async () => {
    (Notifications.getPermissionsAsync as jest.Mock).mockResolvedValue({ status: 'granted' });
    (Notifications.getExpoPushTokenAsync as jest.Mock).mockResolvedValue({
      data: 'ExponentPushToken[abc]',
    });
    (apiClient.post as jest.Mock).mockRejectedValue(new Error('network down'));

    await expect(registerPushToken()).resolves.toBeUndefined();
  });
});
