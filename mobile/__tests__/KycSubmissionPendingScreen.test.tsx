import React from 'react';
import { render, waitFor, act } from '@testing-library/react-native';
import { NavigationContainer } from '@react-navigation/native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import KycSubmissionPendingScreen from '../src/screens/KycSubmissionPendingScreen';
import * as kycApi from '../src/api/kyc';
import * as kycStorage from '../src/storage/kycStorage';
import { AuthProvider } from '../src/auth/AuthContext';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

const mockReset = jest.fn();
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useNavigation: () => ({ reset: mockReset, replace: jest.fn() }),
  useRoute: () => ({ params: { submissionId: 'sub-123' } }),
}));

jest.mock('../src/api/kyc');
jest.mock('../src/storage/kycStorage', () => ({
  clearKycSubmission: jest.fn().mockResolvedValue(undefined),
  markKycApprovalAcknowledged: jest.fn().mockResolvedValue(undefined),
  markKycUnderReviewBannerPending: jest.fn().mockResolvedValue(undefined),
  clearKycUnderReviewBannerPending: jest.fn().mockResolvedValue(undefined),
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

describe('KycSubmissionPendingScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  it('UNDER_REVIEW: shows waiting state', async () => {
    (kycApi.getSubmissionStatus as jest.Mock).mockResolvedValue({
      id: 'sub-123',
      status: 'REVIEWING',
      submitted_at: '',
      updated_at: '',
      rejection_reason: null,
    });

    const utils = render(<KycSubmissionPendingScreen />, { wrapper });
    await waitFor(() => expect(utils.getByText('Under review')).toBeTruthy());
    expect(kycStorage.markKycUnderReviewBannerPending).toHaveBeenCalled();
  });

  it('APPROVED: shows success state and navigates to home', async () => {
    (kycApi.getSubmissionStatus as jest.Mock).mockResolvedValue({
      id: 'sub-123',
      status: 'APPROVED',
      submitted_at: '',
      updated_at: '',
      rejection_reason: null,
    });

    render(<KycSubmissionPendingScreen />, { wrapper });

    await waitFor(() => expect(mockReset).not.toHaveBeenCalled());

    act(() => {
      jest.advanceTimersByTime(2000);
    });
    await waitFor(() => expect(mockReset).toHaveBeenCalled());
    expect(kycStorage.clearKycUnderReviewBannerPending).toHaveBeenCalled();
  });

  it('REJECTED: shows rejection reason', async () => {
    (kycApi.getSubmissionStatus as jest.Mock).mockResolvedValue({
      id: 'sub-123',
      status: 'REJECTED',
      submitted_at: '',
      updated_at: '',
      rejection_reason: 'Card image was unreadable.',
    });

    const utils = render(<KycSubmissionPendingScreen />, { wrapper });
    await waitFor(() => {
      expect(utils.getByText('Verification unsuccessful')).toBeTruthy();
      expect(utils.getByText('Card image was unreadable.')).toBeTruthy();
    });
  });

  it('polls every 10 seconds', async () => {
    (kycApi.getSubmissionStatus as jest.Mock).mockResolvedValue({
      id: 'sub-123',
      status: 'REVIEWING',
      submitted_at: '',
      updated_at: '',
      rejection_reason: null,
    });

    render(<KycSubmissionPendingScreen />, { wrapper });
    await waitFor(() => expect(kycApi.getSubmissionStatus).toHaveBeenCalledTimes(1));

    act(() => {
      jest.advanceTimersByTime(10_000);
    });
    await waitFor(() => expect(kycApi.getSubmissionStatus).toHaveBeenCalledTimes(2));
  });
});
