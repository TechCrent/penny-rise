import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import { AppLockSetupPromptScreen } from '../../screens/AppLockSetupPromptScreen';
import * as appLock from '../../auth/appLock';
import * as LocalAuthentication from 'expo-local-authentication';

jest.mock('../../auth/appLock');

jest.mock('expo-local-authentication', () => ({
  hasHardwareAsync: jest.fn(),
  isEnrolledAsync: jest.fn(),
  authenticateAsync: jest.fn(),
}));

const mockReset = jest.fn();
const nextRoute = { name: 'Main' as const, params: { screen: 'Home' as const } };

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ reset: mockReset }),
  useRoute: () => ({ params: { nextRoute } }),
}));

function typePin(pin: string) {
  for (const digit of pin) {
    fireEvent.press(screen.getByTestId(`pin-key-${digit}`));
  }
}

describe('AppLockSetupPromptScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.mocked(LocalAuthentication.hasHardwareAsync).mockResolvedValue(true);
    jest.mocked(LocalAuthentication.isEnrolledAsync).mockResolvedValue(true);
  });

  it('skipping disables app lock and still navigates to the resolved route', async () => {
    render(<AppLockSetupPromptScreen />);

    fireEvent.press(await screen.findByText('Skip for now'));

    await waitFor(() => expect(appLock.markAppLockSetupPrompted).toHaveBeenCalled());
    expect(appLock.setAppLockEnabled).toHaveBeenCalledWith(false);
    expect(mockReset).toHaveBeenCalledWith({ index: 0, routes: [nextRoute] });
  });

  it('choosing device unlock enables system-method lock and navigates on', async () => {
    render(<AppLockSetupPromptScreen />);

    fireEvent.press(
      await screen.findByText('Use device unlock (Face ID / fingerprint / passcode)'),
    );

    await waitFor(() => expect(appLock.setAppLockMethod).toHaveBeenCalledWith('system'));
    expect(appLock.setAppLockEnabled).toHaveBeenCalledWith(true);
    expect(mockReset).toHaveBeenCalledWith({ index: 0, routes: [nextRoute] });
  });

  it('choosing PIN walks through create + confirm and persists it', async () => {
    render(<AppLockSetupPromptScreen />);

    fireEvent.press(await screen.findByText('Use a 6-digit PIN'));

    await screen.findByText('Create your 6-digit PIN');
    await act(async () => typePin('123456'));

    await screen.findByText('Confirm your PIN');
    await act(async () => typePin('123456'));

    await waitFor(() => expect(appLock.setPin).toHaveBeenCalledWith('123456'));
    expect(appLock.setAppLockMethod).toHaveBeenCalledWith('pin');
    expect(appLock.setAppLockEnabled).toHaveBeenCalledWith(true);
    expect(mockReset).toHaveBeenCalledWith({ index: 0, routes: [nextRoute] });
  });

  it('mismatched PIN confirmation shows an error and does not persist', async () => {
    render(<AppLockSetupPromptScreen />);

    fireEvent.press(await screen.findByText('Use a 6-digit PIN'));
    await screen.findByText('Create your 6-digit PIN');
    await act(async () => typePin('123456'));

    await screen.findByText('Confirm your PIN');
    await act(async () => typePin('654321'));

    expect(await screen.findByText('PINs did not match. Try again.')).toBeTruthy();
    expect(appLock.setPin).not.toHaveBeenCalled();
    expect(mockReset).not.toHaveBeenCalled();
  });

  it('blocks device unlock and both options when no hardware is enrolled', async () => {
    jest.mocked(LocalAuthentication.hasHardwareAsync).mockResolvedValue(false);
    jest.mocked(LocalAuthentication.isEnrolledAsync).mockResolvedValue(false);

    render(<AppLockSetupPromptScreen />);

    fireEvent.press(
      await screen.findByText('Use device unlock (Face ID / fingerprint / passcode)'),
    );

    expect(
      await screen.findByText('No biometrics or device passcode is set up on this device.'),
    ).toBeTruthy();
    expect(appLock.setAppLockMethod).not.toHaveBeenCalled();
    expect(mockReset).not.toHaveBeenCalled();
  });
});
