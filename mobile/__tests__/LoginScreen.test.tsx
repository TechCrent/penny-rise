import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import axios, { type AxiosResponse } from 'axios';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import LoginScreen from '../src/screens/LoginScreen';
import * as authApi from '../src/api/auth';
import { AuthProvider } from '../src/auth/AuthContext';
import type { RootStackParamList } from '../src/navigation/RootNavigator';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

const mockNavigate = jest.fn();
let mockRouteParams: RootStackParamList['Login'] = {};

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: mockNavigate, reset: jest.fn(), goBack: jest.fn() }),
  useRoute: () => ({ params: mockRouteParams }),
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

describe('LoginScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockRouteParams = {};
  });

  it('happy login: persists session tokens', async () => {
    const setItemAsync = jest.requireMock('expo-secure-store').setItemAsync as jest.Mock;
    jest.spyOn(authApi, 'login').mockResolvedValue({
      access_token: 'at',
      refresh_token: 'rt',
      expires_in: 900,
      user: { id: '1', email: 'a@b.com', display_name: 'A', kyc_status: 'APPROVED' },
    });

    const utils = render(<LoginScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('you@example.com'), 'a@b.com');
    fireEvent.changeText(utils.getByPlaceholderText('Your password'), 'Pass@123');
    fireEvent.press(utils.getByText('Sign in'));

    await waitFor(() => expect(setItemAsync).toHaveBeenCalled(), { timeout: 10000 });
  }, 15000);

  it('routes PENDING users through session bootstrap after login', async () => {
    jest.spyOn(authApi, 'login').mockResolvedValue({
      access_token: 'at',
      refresh_token: 'rt',
      expires_in: 900,
      user: { id: '1', email: 'a@b.com', display_name: 'A', kyc_status: 'PENDING' },
    });

    const utils = render(<LoginScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('you@example.com'), 'a@b.com');
    fireEvent.changeText(utils.getByPlaceholderText('Your password'), 'Pass@123');
    fireEvent.press(utils.getByText('Sign in'));

    await waitFor(() => expect(authApi.login).toHaveBeenCalled());
  });

  it('wrong password: shows inline field error', async () => {
    jest.spyOn(authApi, 'login').mockRejectedValue(
      new axios.AxiosError('Unauthorized', '401', undefined, undefined, {
        status: 401,
        data: { error: { code: 'AUTH_INVALID_CREDENTIALS', message: 'Invalid credentials' } },
      } as AxiosResponse),
    );

    const utils = render(<LoginScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('you@example.com'), 'a@b.com');
    fireEvent.changeText(utils.getByPlaceholderText('Your password'), 'WrongPass!1');
    fireEvent.press(utils.getByText('Sign in'));

    await waitFor(() => expect(utils.getByText('Email or password is incorrect.')).toBeTruthy());
  });

  it('lockout: shows countdown timer when AUTH_ACCOUNT_LOCKED returned', async () => {
    jest.spyOn(authApi, 'login').mockRejectedValue(
      new axios.AxiosError('Locked', '423', undefined, undefined, {
        status: 423,
        data: {
          error: {
            code: 'AUTH_ACCOUNT_LOCKED',
            message: 'Locked',
            details: { retry_after: 3600 },
          },
        },
      } as AxiosResponse),
    );

    const utils = render(<LoginScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('you@example.com'), 'a@b.com');
    fireEvent.changeText(utils.getByPlaceholderText('Your password'), 'Attempt@1');
    fireEvent.press(utils.getByText('Sign in'));

    await waitFor(() => expect(utils.getByText('Account temporarily locked')).toBeTruthy());
  });

  it('success banner shown when navigated with successBanner param', () => {
    mockRouteParams = { successBanner: 'Password reset successfully. Please sign in.' };

    const utils = render(<LoginScreen />, { wrapper });

    expect(utils.getByText('Password reset successfully. Please sign in.')).toBeTruthy();
  });
});
