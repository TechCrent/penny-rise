import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import EmailVerificationPendingScreen from '../src/screens/EmailVerificationPendingScreen';
import * as authApi from '../src/api/auth';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

const mockReset = jest.fn();

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: jest.fn(), reset: mockReset, goBack: jest.fn() }),
  useRoute: () => ({ params: { email: 'user@example.com', token: 'verify-token' } }),
}));

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <SafeAreaProvider
    initialMetrics={{
      frame: { x: 0, y: 0, width: 390, height: 844 },
      insets: { top: 0, left: 0, right: 0, bottom: 0 },
    }}
  >
    <NavigationContainer>{children}</NavigationContainer>
  </SafeAreaProvider>
);

describe('EmailVerificationPendingScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('verifies email from deep link token and navigates to login', async () => {
    jest.spyOn(authApi, 'verifyEmail').mockResolvedValue(undefined);

    render(<EmailVerificationPendingScreen />, { wrapper });

    await waitFor(() => expect(authApi.verifyEmail).toHaveBeenCalledWith('verify-token'));
    await waitFor(
      () =>
        expect(mockReset).toHaveBeenCalledWith({
          index: 0,
          routes: [
            {
              name: 'Login',
              params: { successBanner: 'Email verified! You can sign in now.' },
            },
          ],
        }),
      { timeout: 10_000 },
    );
  }, 15_000);
});
