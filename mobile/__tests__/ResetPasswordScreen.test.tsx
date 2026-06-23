import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import axios, { type AxiosResponse } from 'axios';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import ResetPasswordScreen from '../src/screens/ResetPasswordScreen';
import * as authApi from '../src/api/auth';
import { AuthProvider } from '../src/auth/AuthContext';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: mockNavigate }),
  useRoute: () => ({ params: { token: 'test-reset-token' } }),
}));

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <SafeAreaProvider
    initialMetrics={{
      frame: { x: 0, y: 0, width: 390, height: 844 },
      insets: { top: 0, left: 0, right: 0, bottom: 0 },
    }}
  >
    <AuthProvider>
      <NavigationContainer>{children}</NavigationContainer>
    </AuthProvider>
  </SafeAreaProvider>
);

describe('ResetPasswordScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('happy reset: navigates to login with success banner', async () => {
    jest.spyOn(authApi, 'resetPassword').mockResolvedValue(undefined);

    const utils = render(<ResetPasswordScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('Min. 8 characters'), 'NewPass@1');
    fireEvent.changeText(utils.getByPlaceholderText('Repeat your new password'), 'NewPass@1');
    fireEvent.press(utils.getByText('Set new password'));

    await waitFor(() =>
      expect(mockNavigate).toHaveBeenCalledWith('Login', {
        successBanner: 'Password reset successfully. Please sign in.',
      }),
    );
  });

  it('mismatched passwords: shows validation error before API call', async () => {
    const spy = jest.spyOn(authApi, 'resetPassword');

    const utils = render(<ResetPasswordScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('Min. 8 characters'), 'NewPass@1');
    fireEvent.changeText(utils.getByPlaceholderText('Repeat your new password'), 'Different@1');
    fireEvent.press(utils.getByText('Set new password'));

    await waitFor(() => expect(utils.getByText('Passwords do not match')).toBeTruthy());
    expect(spy).not.toHaveBeenCalled();
  });

  it('expired token: shows expired error with request-new-link', async () => {
    jest.spyOn(authApi, 'resetPassword').mockRejectedValue(
      new axios.AxiosError('Gone', '410', undefined, undefined, {
        status: 410,
        data: { error: { code: 'AUTH_RESET_TOKEN_EXPIRED', message: 'Expired' } },
      } as AxiosResponse),
    );

    const utils = render(<ResetPasswordScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('Min. 8 characters'), 'NewPass@1');
    fireEvent.changeText(utils.getByPlaceholderText('Repeat your new password'), 'NewPass@1');
    fireEvent.press(utils.getByText('Set new password'));

    await waitFor(() => expect(utils.getByText(/This reset link has expired/)).toBeTruthy());
    expect(utils.getByText('Request a new reset link →')).toBeTruthy();
  });
});
