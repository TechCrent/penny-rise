import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import ForgotPasswordScreen from '../src/screens/ForgotPasswordScreen';
import * as authApi from '../src/api/auth';
import { AuthProvider } from '../src/auth/AuthContext';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: jest.fn(), goBack: jest.fn() }),
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

describe('ForgotPasswordScreen', () => {
  it('happy path: shows confirmation screen after submission', async () => {
    jest.spyOn(authApi, 'forgotPassword').mockResolvedValue(undefined);

    const utils = render(<ForgotPasswordScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('you@example.com'), 'user@example.com');
    fireEvent.press(utils.getByText('Send reset link'));

    await waitFor(() => expect(utils.getByText('Check your inbox')).toBeTruthy());
  });

  it('no-enumeration: shows same confirmation even on network failure', async () => {
    jest.spyOn(authApi, 'forgotPassword').mockRejectedValue(new Error('Network error'));

    const utils = render(<ForgotPasswordScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('you@example.com'), 'unknown@example.com');
    fireEvent.press(utils.getByText('Send reset link'));

    await waitFor(() => expect(utils.getByText('Check your inbox')).toBeTruthy());
  });
});
