import React from 'react';
import { render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NavigationContainer } from '@react-navigation/native';
import { ChallengesListScreen } from '../../../src/screens/Challenges/ChallengesListScreen';
import * as challengesApi from '../../../src/api/challengesApi';
import type { Challenge } from '../../../src/screens/Challenges/types';

jest.mock('../../../src/api/challengesApi');

function renderScreen() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <NavigationContainer>
        <ChallengesListScreen />
      </NavigationContainer>
    </QueryClientProvider>,
  );
}

const availableChallenge: Challenge = {
  id: 'c-1',
  name: 'Save GHS 50 in 7 Days',
  description: 'A quick starter challenge.',
  targetAmount: 5000,
  targetDurationDays: 7,
  badgeCode: 'STARTER_SAVER',
  badgeName: 'Starter Saver',
  badgeAssetName: 'badge_starter_saver',
  enrollment: null,
};

const activeChallenge: Challenge = {
  ...availableChallenge,
  id: 'c-2',
  name: 'Save GHS 200 in 30 Days',
  targetAmount: 20000,
  enrollment: {
    status: 'ACTIVE',
    progressAmount: 10000,
    enrolledAt: '2026-06-01T00:00:00Z',
    completedAt: null,
  },
};

const completedChallenge: Challenge = {
  ...availableChallenge,
  id: 'c-3',
  name: 'Save GHS 1000 in 90 Days',
  targetAmount: 100000,
  enrollment: {
    status: 'COMPLETED',
    progressAmount: 100000,
    enrolledAt: '2026-04-01T00:00:00Z',
    completedAt: '2026-06-30T00:00:00Z',
  },
};

describe('ChallengesListScreen', () => {
  beforeEach(() => jest.clearAllMocks());

  it('renders Active, Available, and Completed sections when all three have entries', async () => {
    (challengesApi.fetchChallenges as jest.Mock).mockResolvedValue([
      availableChallenge,
      activeChallenge,
      completedChallenge,
    ]);

    renderScreen();

    expect(await screen.findByText('Active')).toBeTruthy();
    expect(screen.getByText('Available')).toBeTruthy();
    expect(screen.getByText('Completed')).toBeTruthy();
    expect(screen.getByText('Save GHS 50 in 7 Days')).toBeTruthy();
    expect(screen.getByText('Save GHS 200 in 30 Days')).toBeTruthy();
    expect(screen.getByText('Save GHS 1000 in 90 Days')).toBeTruthy();
  });

  it('shows a progress bar for active challenges', async () => {
    (challengesApi.fetchChallenges as jest.Mock).mockResolvedValue([activeChallenge]);

    renderScreen();

    await waitFor(() => expect(screen.getByTestId('challenge-progress-bar')).toBeTruthy());
    expect(screen.getByText('50% complete')).toBeTruthy();
  });

  it('shows completion date for completed challenges, not a progress bar', async () => {
    (challengesApi.fetchChallenges as jest.Mock).mockResolvedValue([completedChallenge]);

    renderScreen();

    await waitFor(() => expect(screen.getByTestId('challenge-completed-label')).toBeTruthy());
    expect(screen.queryByTestId('challenge-progress-bar')).toBeNull();
  });

  it('does not render a section with zero challenges', async () => {
    (challengesApi.fetchChallenges as jest.Mock).mockResolvedValue([availableChallenge]);

    renderScreen();

    await screen.findByText('Available');
    expect(screen.queryByText('Active')).toBeNull();
    expect(screen.queryByText('Completed')).toBeNull();
  });

  it('shows an empty state when there are no challenges at all', async () => {
    (challengesApi.fetchChallenges as jest.Mock).mockResolvedValue([]);

    renderScreen();

    expect(await screen.findByText('No challenges available right now.')).toBeTruthy();
  });
});
