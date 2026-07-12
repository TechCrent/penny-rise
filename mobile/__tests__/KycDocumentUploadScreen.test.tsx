import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import KycDocumentUploadScreen from '../src/screens/KycDocumentUploadScreen';
import * as kycStorage from '../src/storage/kycStorage';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

const mockReset = jest.fn();
const mockNavigate = jest.fn();
const ROUTE_PARAMS = {
  submissionId: 'sub-1',
  uploadUrls: {
    FRONT_OF_CARD: 'https://api.example.com/upload/front',
    BACK_OF_CARD: 'https://api.example.com/upload/back',
    SELFIE: 'https://api.example.com/upload/selfie',
  },
};

jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ reset: mockReset, navigate: mockNavigate }),
  useRoute: () => ({ params: ROUTE_PARAMS }),
}));

jest.mock('../src/auth/AuthContext', () => ({
  useAuth: () => ({ accessToken: 'header.eyJzdWIiOiJ1c2VyLTEifQ.sig' }),
}));

jest.mock('../src/storage/kycStorage', () => ({
  saveKycSubmission: jest.fn().mockResolvedValue(undefined),
  clearKycSubmission: jest.fn().mockResolvedValue(undefined),
  loadKycSubmission: jest.fn(),
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

describe('KycDocumentUploadScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    // Resume path: all 3 documents were already uploaded in a prior session
    // for this exact user+submission, so the screen restores 'success'
    // state for each without needing to simulate a real file upload.
    (kycStorage.loadKycSubmission as jest.Mock).mockResolvedValue({
      submissionId: 'sub-1',
      ownerUserId: 'user-1',
      uploadUrls: ROUTE_PARAMS.uploadUrls,
      uploadedTypes: ['FRONT_OF_CARD', 'BACK_OF_CARD', 'SELFIE'],
    });
  });

  it('resets (not navigates) to KycSubmissionPending on submit, dropping the form off the stack', async () => {
    const utils = render(<KycDocumentUploadScreen />, { wrapper });

    await waitFor(() => expect(utils.getByText('All 3 documents uploaded')).toBeTruthy());

    fireEvent.press(utils.getByText('Submit for review'));

    expect(mockNavigate).not.toHaveBeenCalled();
    expect(mockReset).toHaveBeenCalledWith({
      index: 0,
      routes: [{ name: 'KycSubmissionPending', params: { submissionId: 'sub-1' } }],
    });
  });
});
