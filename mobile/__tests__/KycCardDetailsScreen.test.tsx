import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import axios, { type AxiosResponse } from 'axios';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import KycCardDetailsScreen from '../src/screens/KycCardDetailsScreen';
import * as kycApi from '../src/api/kyc';
import { AuthProvider } from '../src/auth/AuthContext';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../src/hooks/useKycResumability', () => ({
  useKycResumability: jest.fn(),
}));

const mockNavigate = jest.fn();
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ navigate: mockNavigate, replace: jest.fn() }),
}));

jest.mock('../src/api/kyc', () => ({
  ...jest.requireActual('../src/api/kyc'),
  getMySubmission: jest.fn().mockResolvedValue(null),
  createSubmission: jest.fn(),
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

describe('KycCardDetailsScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('Ghana Card field starts pre-filled with the GHA- prefix', () => {
    const utils = render(<KycCardDetailsScreen />, { wrapper });
    expect(utils.getByPlaceholderText('GHA-000000000-0').props.value).toBe('GHA-');
  });

  it('typing raw digits auto-formats with dashes and caps at 10 digits', () => {
    const utils = render(<KycCardDetailsScreen />, { wrapper });

    fireEvent.changeText(utils.getByPlaceholderText('GHA-000000000-0'), 'GHA-12345678901234');

    expect(utils.getByPlaceholderText('GHA-000000000-0').props.value).toBe('GHA-123456789-0');
  });

  it('happy path: creates submission and navigates to doc upload', async () => {
    (kycApi.createSubmission as jest.Mock).mockResolvedValue({
      id: 'sub-123',
      status: 'PENDING_DOCUMENTS',
      upload_urls: { FRONT_OF_CARD: 'url1', BACK_OF_CARD: 'url2', SELFIE: 'url3' },
    });

    const utils = render(<KycCardDetailsScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('GHA-000000000-0'), 'GHA-000000001-1');
    fireEvent.changeText(utils.getByPlaceholderText('e.g. KWAME MENSAH ASANTE'), 'KWAME MENSAH');
    fireEvent.press(utils.getByText('Continue'));

    await waitFor(() => {
      expect(kycApi.createSubmission).toHaveBeenCalledWith({
        ghana_card_number: 'GHA-000000001-1',
        full_name: 'KWAME MENSAH',
      });
      expect(mockNavigate).toHaveBeenCalledWith(
        'KycDocumentUpload',
        expect.objectContaining({ submissionId: 'sub-123' }),
      );
    });
  });

  it('invalid Ghana Card format: shows inline validation error', async () => {
    const utils = render(<KycCardDetailsScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('GHA-000000000-0'), 'GHA-12345');
    fireEvent.changeText(utils.getByPlaceholderText('e.g. KWAME MENSAH ASANTE'), 'KWAME');
    fireEvent.press(utils.getByText('Continue'));

    await waitFor(() => expect(utils.getByText(/Format must be GHA-XXXXXXXXX-X/)).toBeTruthy());
    expect(kycApi.createSubmission).not.toHaveBeenCalled();
  });

  it('active submission conflict: shows global error', async () => {
    (kycApi.createSubmission as jest.Mock).mockRejectedValue(
      new axios.AxiosError('Conflict', '409', undefined, undefined, {
        status: 409,
        data: { error: { code: 'KYC_SUBMISSION_ALREADY_ACTIVE', message: 'Active' } },
      } as AxiosResponse),
    );

    const utils = render(<KycCardDetailsScreen />, { wrapper });
    fireEvent.changeText(utils.getByPlaceholderText('GHA-000000000-0'), 'GHA-000000001-1');
    fireEvent.changeText(utils.getByPlaceholderText('e.g. KWAME MENSAH ASANTE'), 'KWAME');
    fireEvent.press(utils.getByText('Continue'));

    await waitFor(() =>
      expect(utils.getByText(/already have an active KYC submission/)).toBeTruthy(),
    );
  });
});
