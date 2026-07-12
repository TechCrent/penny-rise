import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import { AppLockSettingsScreen } from '../../../screens/Settings/AppLockSettingsScreen';
import * as appLock from '../../../auth/appLock';
import * as LocalAuthentication from 'expo-local-authentication';

jest.mock('../../../auth/appLock');

jest.mock('expo-local-authentication', () => ({
  hasHardwareAsync: jest.fn(),
  isEnrolledAsync: jest.fn(),
  authenticateAsync: jest.fn(),
}));

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ goBack: jest.fn() }),
}));

function typePin(pin: string) {
  for (const digit of pin) {
    fireEvent.press(screen.getByTestId(`pin-key-${digit}`));
  }
}

function mockLoad(overrides: {
  enabled?: boolean;
  method?: appLock.AppLockMethod | null;
  pinSet?: boolean;
  hasHardware?: boolean;
  isEnrolled?: boolean;
}) {
  jest.mocked(appLock.isAppLockEnabled).mockResolvedValue(overrides.enabled ?? false);
  jest.mocked(appLock.getAppLockMethod).mockResolvedValue(overrides.method ?? null);
  jest.mocked(appLock.hasPin).mockResolvedValue(overrides.pinSet ?? false);
  jest.mocked(LocalAuthentication.hasHardwareAsync).mockResolvedValue(overrides.hasHardware ?? true);
  jest.mocked(LocalAuthentication.isEnrolledAsync).mockResolvedValue(overrides.isEnrolled ?? true);
}

describe('AppLockSettingsScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('toggling off when already enabled just disables, without re-prompting for a method', async () => {
    mockLoad({ enabled: true, method: 'system' });
    render(<AppLockSettingsScreen />);

    const toggle = await screen.findByRole('switch');
    fireEvent(toggle, 'valueChange', false);

    await waitFor(() => expect(appLock.setAppLockEnabled).toHaveBeenCalledWith(false));
    expect(appLock.setAppLockMethod).not.toHaveBeenCalled();
  });

  it('turning on for the first time (no method yet) prompts to choose a method', async () => {
    mockLoad({ enabled: false, method: null });
    render(<AppLockSettingsScreen />);

    const toggle = await screen.findByRole('switch');
    fireEvent(toggle, 'valueChange', true);

    expect(await screen.findByText('Choose unlock method')).toBeTruthy();
  });

  it('choosing PIN from the method picker walks through create + confirm', async () => {
    mockLoad({ enabled: false, method: null });
    render(<AppLockSettingsScreen />);

    fireEvent(await screen.findByRole('switch'), 'valueChange', true);
    fireEvent.press(await screen.findByText('6-digit PIN'));

    await screen.findByText('Create your 6-digit PIN');
    await act(async () => typePin('123456'));
    await screen.findByText('Confirm your PIN');
    await act(async () => typePin('123456'));

    await waitFor(() => expect(appLock.setPin).toHaveBeenCalledWith('123456'));
    expect(appLock.setAppLockMethod).toHaveBeenCalledWith('pin');
    expect(appLock.setAppLockEnabled).toHaveBeenCalledWith(true);
  });

  it('shows method and PIN management links once enabled with a PIN method', async () => {
    mockLoad({ enabled: true, method: 'pin', pinSet: true });
    render(<AppLockSettingsScreen />);

    expect(await screen.findByText('PIN')).toBeTruthy();
    expect(screen.getByText('Change method')).toBeTruthy();
    expect(screen.getByText('Change PIN')).toBeTruthy();
    expect(screen.getByText('Remove PIN')).toBeTruthy();
  });

  it('removing the PIN clears it and falls back to the system method', async () => {
    mockLoad({ enabled: true, method: 'pin', pinSet: true });
    render(<AppLockSettingsScreen />);

    fireEvent.press(await screen.findByText('Remove PIN'));

    await waitFor(() => expect(appLock.clearPin).toHaveBeenCalled());
    expect(appLock.setAppLockMethod).toHaveBeenCalledWith('system');
  });

  it('does not offer PIN management links for the system-only method', async () => {
    mockLoad({ enabled: true, method: 'system' });
    render(<AppLockSettingsScreen />);

    await screen.findByText('Device unlock');
    expect(screen.queryByText('Change PIN')).toBeNull();
    expect(screen.queryByText('Remove PIN')).toBeNull();
  });
});
