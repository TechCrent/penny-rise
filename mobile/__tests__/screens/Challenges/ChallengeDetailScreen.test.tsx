import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NavigationContainer } from '@react-navigation/native';
import { ChallengeDetailScreen } from '../../../src/screens/Challenges/ChallengeDetailScreen';
import * as challengesApi from '../../../src/api/challengesApi';
import type { Challenge } from '../../../src/screens/Challenges/types';

jest.mock('../../../src/api/challengesApi');
jest.mock('@react-navigation/native', () => ({
  ...jest.requireActual('@react-navigation/native'),
  useRoute: () => ({ params: { challengeId: 'c-1' } }),
}));

function renderScreen() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <NavigationContainer>
        <ChallengeDetailScreen />
      </NavigationContainer>
    </QueryClientProvider>,
  );
}

const unenrolledChallenge: Challenge = {
  id: 'c-1',
  name: 'Save GHS 50 in 7 Days',
  description: 'A quick starter challenge — save GHS 50 within a week.',
  targetAmount: 5000,
  targetDurationDays: 7,
  badgeCode: 'STARTER_SAVER',
  badgeName: 'Starter Saver',
  badgeAssetName: 'badge_starter_saver',
  enrollment: null,
};

describe('ChallengeDetailScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('shows an Enrol CTA for an unenrolled challenge', async () => {
    (challengesApi.fetchChallengeDetail as jest.Mock).mockResolvedValue(unenrolledChallenge);

    renderScreen();

    expect(await screen.findByLabelText('Join this challenge')).toBeTruthy();
  });

  it('opens the confirmation sheet on tapping the enrol CTA', async () => {
    (challengesApi.fetchChallengeDetail as jest.Mock).mockResolvedValue(unenrolledChallenge);

    renderScreen();

    fireEvent.press(await screen.findByLabelText('Join this challenge'));

    expect(await screen.findByText('Join "Save GHS 50 in 7 Days"?')).toBeTruthy();
  });

  it('confirming enrollment calls POST /join and closes the sheet on success', async () => {
    (challengesApi.fetchChallengeDetail as jest.Mock).mockResolvedValue(unenrolledChallenge);
    (challengesApi.joinChallenge as jest.Mock).mockResolvedValue({
      id: 'uc-1',
      challengeId: 'c-1',
      status: 'ACTIVE',
      targetAmount: 5000,
      progressAmount: 0,
      enrolledAt: '2026-07-01T00:00:00Z',
    });

    renderScreen();
    fireEvent.press(await screen.findByLabelText('Join this challenge'));
    fireEvent.press(await screen.findByLabelText('Confirm join challenge'));

    await waitFor(() =>
      expect(challengesApi.joinChallenge).toHaveBeenCalledWith('c-1', expect.anything()),
    );
    await waitFor(() => expect(screen.queryByText('Join "Save GHS 50 in 7 Days"?')).toBeNull());
  });

  it('does NOT show the enrol CTA for an already-active enrollment', async () => {
    (challengesApi.fetchChallengeDetail as jest.Mock).mockResolvedValue({
      ...unenrolledChallenge,
      enrollment: {
        status: 'ACTIVE',
        progressAmount: 2500,
        enrolledAt: '2026-06-25T00:00:00Z',
        completedAt: null,
      },
    });

    renderScreen();

    await screen.findByTestId('detail-progress-section');
    expect(screen.queryByLabelText('Join this challenge')).toBeNull();
  });

  it('shows progress amount, percentage, and enrolled date for an active enrollment', async () => {
    (challengesApi.fetchChallengeDetail as jest.Mock).mockResolvedValue({
      ...unenrolledChallenge,
      enrollment: {
        status: 'ACTIVE',
        progressAmount: 2500,
        enrolledAt: '2026-06-25T00:00:00Z',
        completedAt: null,
      },
    });

    renderScreen();

    expect(await screen.findByText(/50%/)).toBeTruthy();
    expect(screen.getByText(/Enrolled/)).toBeTruthy();
  });

  it('completed state shows the earned badge label and completion date, no CTA', async () => {
    (challengesApi.fetchChallengeDetail as jest.Mock).mockResolvedValue({
      ...unenrolledChallenge,
      enrollment: {
        status: 'COMPLETED',
        progressAmount: 5000,
        enrolledAt: '2026-06-01T00:00:00Z',
        completedAt: '2026-06-08T00:00:00Z',
      },
    });

    renderScreen();

    expect(await screen.findByTestId('badge-earned-label')).toBeTruthy();
    expect(screen.queryByLabelText('Join this challenge')).toBeNull();
  });
});
