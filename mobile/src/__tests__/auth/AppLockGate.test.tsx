import React from 'react';
import { Text } from 'react-native';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import { AppLockGate } from '../../auth/AppLockGate';
import * as useAuthModule from '../../auth/AuthContext';
import * as appLock from '../../auth/appLock';
import * as LocalAuthentication from 'expo-local-authentication';

jest.mock('../../auth/AuthContext', () => ({
  useAuth: jest.fn(),
}));

jest.mock('../../auth/appLock');

jest.mock('expo-local-authentication', () => ({
  authenticateAsync: jest.fn(),
}));

function typePin(pin: string) {
  for (const digit of pin) {
    fireEvent.press(screen.getByTestId(`pin-key-${digit}`));
  }
}

describe('AppLockGate', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(useAuthModule.useAuth).mockReturnValue({
      isAuthenticated: true,
      accessToken: 'token',
      kycStatus: 'APPROVED',
      isLoading: false,
      justLoggedIn: false,
      setTokens: jest.fn(),
      clearTokens: jest.fn(),
    });
  });

  it('renders children directly when app lock is disabled', async () => {
    jest.mocked(appLock.isAppLockEnabled).mockResolvedValue(false);
    jest.mocked(appLock.getAppLockMethod).mockResolvedValue(null);

    render(
      <AppLockGate>
        <Text>Home content</Text>
      </AppLockGate>,
    );

    expect(await screen.findByText('Home content')).toBeTruthy();
  });

  it('shows a PIN pad and unlocks on a correct PIN', async () => {
    jest.mocked(appLock.isAppLockEnabled).mockResolvedValue(true);
    jest.mocked(appLock.getAppLockMethod).mockResolvedValue('pin');
    jest.mocked(appLock.verifyPin).mockResolvedValue(true);

    render(
      <AppLockGate>
        <Text>Home content</Text>
      </AppLockGate>,
    );

    expect(await screen.findByText('PennyRise is locked')).toBeTruthy();
    expect(screen.queryByText('Home content')).toBeNull();

    await act(async () => typePin('123456'));

    await waitFor(() => expect(screen.getByText('Home content')).toBeTruthy());
    expect(appLock.verifyPin).toHaveBeenCalledWith('123456');
  });

  it('shows an error and stays locked on an incorrect PIN', async () => {
    jest.mocked(appLock.isAppLockEnabled).mockResolvedValue(true);
    jest.mocked(appLock.getAppLockMethod).mockResolvedValue('pin');
    jest.mocked(appLock.verifyPin).mockResolvedValue(false);

    render(
      <AppLockGate>
        <Text>Home content</Text>
      </AppLockGate>,
    );

    await screen.findByText('PennyRise is locked');
    await act(async () => typePin('000000'));

    expect(await screen.findByText('Incorrect PIN. Try again.')).toBeTruthy();
    expect(screen.queryByText('Home content')).toBeNull();
  });

  it('auto-prompts system unlock and unlocks on success for the system method', async () => {
    jest.mocked(appLock.isAppLockEnabled).mockResolvedValue(true);
    jest.mocked(appLock.getAppLockMethod).mockResolvedValue('system');
    jest.mocked(LocalAuthentication.authenticateAsync).mockResolvedValue({
      success: true,
    } as LocalAuthentication.LocalAuthenticationResult);

    render(
      <AppLockGate>
        <Text>Home content</Text>
      </AppLockGate>,
    );

    await waitFor(() => expect(LocalAuthentication.authenticateAsync).toHaveBeenCalled());
    await waitFor(() => expect(screen.getByText('Home content')).toBeTruthy());
  });

  it('offers a PIN fallback for the both method', async () => {
    jest.mocked(appLock.isAppLockEnabled).mockResolvedValue(true);
    jest.mocked(appLock.getAppLockMethod).mockResolvedValue('both');
    jest.mocked(appLock.verifyPin).mockResolvedValue(true);
    jest.mocked(LocalAuthentication.authenticateAsync).mockResolvedValue({
      success: false,
    } as LocalAuthentication.LocalAuthenticationResult);

    render(
      <AppLockGate>
        <Text>Home content</Text>
      </AppLockGate>,
    );

    const switchLink = await screen.findByText('Use PIN instead');
    fireEvent.press(switchLink);

    await act(async () => typePin('123456'));

    await waitFor(() => expect(screen.getByText('Home content')).toBeTruthy());
  });
});
