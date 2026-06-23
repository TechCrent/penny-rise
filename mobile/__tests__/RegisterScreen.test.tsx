import React from 'react';
import { render, fireEvent, waitFor, act } from '@testing-library/react-native';
import { ActivityIndicator } from 'react-native';
import axios, { type AxiosResponse } from 'axios';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import RegisterScreen from '../src/screens/RegisterScreen';
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

function fillForm(
  utils: ReturnType<typeof render>,
  overrides: Partial<{ name: string; email: string; password: string }> = {},
) {
  const { getByPlaceholderText } = utils;
  fireEvent.changeText(
    getByPlaceholderText('How should we call you?'),
    overrides.name ?? 'Test User',
  );
  fireEvent.changeText(
    getByPlaceholderText('you@example.com'),
    overrides.email ?? 'test@example.com',
  );
  fireEvent.changeText(
    getByPlaceholderText('Min. 8 characters'),
    overrides.password ?? 'SecureP@ss1',
  );
}

describe('RegisterScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('happy submission: calls signup API and navigates to verification pending', async () => {
    const mockSignup = jest.spyOn(authApi, 'signup').mockResolvedValue({
      id: 'abc-123',
      email: 'test@example.com',
      display_name: 'Test User',
      message: 'Created',
    });

    const utils = render(<RegisterScreen />, { wrapper });
    fillForm(utils);

    fireEvent.press(utils.getByText('Create account'));

    await waitFor(() => {
      expect(mockSignup).toHaveBeenCalledWith({
        display_name: 'Test User',
        email: 'test@example.com',
        password: 'SecureP@ss1',
        referral_code: undefined,
      });
      expect(mockNavigate).toHaveBeenCalledWith('EmailVerificationPending', {
        email: 'test@example.com',
      });
    });
  });

  it('duplicate email: 409 surfaces as inline field error on email field', async () => {
    jest.spyOn(authApi, 'signup').mockRejectedValue(
      new axios.AxiosError('Conflict', '409', undefined, undefined, {
        status: 409,
        data: {
          error: { code: 'AUTH_EMAIL_ALREADY_REGISTERED', message: 'Already exists.' },
        },
      } as AxiosResponse),
    );

    const utils = render(<RegisterScreen />, { wrapper });
    fillForm(utils);
    fireEvent.press(utils.getByText('Create account'));

    await waitFor(() => {
      expect(utils.getByText('An account with this email already exists.')).toBeTruthy();
    });
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  it('weak password: inline validation error shown before API call', async () => {
    const mockSignup = jest.spyOn(authApi, 'signup');
    const utils = render(<RegisterScreen />, { wrapper });
    fillForm(utils, { password: 'weak' });

    fireEvent.press(utils.getByText('Create account'));

    await waitFor(() => {
      expect(utils.getByText('Password must be at least 8 characters')).toBeTruthy();
    });
    expect(mockSignup).not.toHaveBeenCalled();
  });

  it('loading state: button shows loading indicator while submitting', async () => {
    let resolveSignup!: (v: authApi.SignupResponse) => void;
    jest.spyOn(authApi, 'signup').mockImplementation(
      () => new Promise(res => { resolveSignup = res; }),
    );

    const utils = render(<RegisterScreen />, { wrapper });
    fillForm(utils);
    fireEvent.press(utils.getByText('Create account'));

    await waitFor(() => {
      expect(utils.UNSAFE_getAllByType(ActivityIndicator).length).toBeGreaterThan(0);
    });

    act(() => {
      resolveSignup({
        id: 'x',
        email: 'test@example.com',
        display_name: 'Test',
        message: 'ok',
      });
    });
  });

  it('double-submission prevention: second press while loading is ignored', async () => {
    let resolveSignup!: (v: authApi.SignupResponse) => void;
    const mockSignup = jest.spyOn(authApi, 'signup').mockImplementation(
      () => new Promise(res => { resolveSignup = res; }),
    );

    const utils = render(<RegisterScreen />, { wrapper });
    fillForm(utils);
    fireEvent.press(utils.getByText('Create account'));
    fireEvent.press(utils.getByText('Create account'));

    await waitFor(() => expect(mockSignup).toHaveBeenCalledTimes(1));

    act(() => {
      resolveSignup({
        id: 'x',
        email: 'test@example.com',
        display_name: 'Test',
        message: 'ok',
      });
    });
  });

  it('referral field hidden by default, revealed on toggle', () => {
    const utils = render(<RegisterScreen />, { wrapper });

    expect(utils.queryByPlaceholderText('e.g. FRIEND50')).toBeNull();

    fireEvent.press(utils.getByText('Have a referral code?'));

    expect(utils.getByPlaceholderText('e.g. FRIEND50')).toBeTruthy();
  });
});
