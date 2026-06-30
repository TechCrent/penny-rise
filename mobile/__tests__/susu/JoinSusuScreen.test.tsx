import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import { JoinSusuScreen } from '../../src/screens/susu/JoinSusuScreen';
import * as api from '../../src/api/susu';

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: jest.fn() }),
}));

const mockJoinResult = {
  group: {
    id: 'g1',
    name: "Akua's Circle",
    contribution_amount_cedis: '200.00',
    frequency: 'MONTHLY',
    target_member_count: 6,
    current_member_count: 2,
    status: 'PENDING',
  },
  membership: { id: 'mem-1', status: 'ACTIVE' },
};

describe('JoinSusuScreen', () => {
  afterEach(() => jest.restoreAllMocks());

  it('renders code input boxes', () => {
    render(<JoinSusuScreen />);
    for (let i = 0; i < 8; i++) {
      expect(screen.getByTestId(`code-box-${i}`)).toBeTruthy();
    }
  });

  it('shows group preview after successful join', async () => {
    jest.spyOn(api.susuApi, 'joinGroup').mockResolvedValue(mockJoinResult);
    render(<JoinSusuScreen />);

    await act(async () => {
      fireEvent.changeText(screen.getByTestId('hidden-code-input'), 'STSH1234');
    });

    await waitFor(() => {
      expect(screen.getByTestId('join-preview')).toBeTruthy();
      expect(screen.getByText("Akua's Circle")).toBeTruthy();
      expect(screen.getByTestId('joined-badge')).toBeTruthy();
    });
  });

  it('shows error for unknown code (404)', async () => {
    jest.spyOn(api.susuApi, 'joinGroup').mockRejectedValue({ status: 404 });
    render(<JoinSusuScreen />);

    await act(async () => {
      fireEvent.changeText(screen.getByTestId('hidden-code-input'), 'XXXXXXXX');
    });

    await waitFor(() => {
      expect(screen.getByTestId('join-error')).toBeTruthy();
      expect(screen.getByText(/No susu found/)).toBeTruthy();
    });
  });

  it('shows group full error', async () => {
    jest.spyOn(api.susuApi, 'joinGroup').mockRejectedValue({
      code: 'SUSU_GROUP_FULL',
    });
    render(<JoinSusuScreen />);

    await act(async () => {
      fireEvent.changeText(screen.getByTestId('hidden-code-input'), 'FULLCODE');
    });

    await waitFor(() => {
      expect(screen.getByTestId('join-error')).toBeTruthy();
      expect(screen.getByText(/full/i)).toBeTruthy();
    });
  });

  it('shows already-a-member error with view-group link', async () => {
    jest.spyOn(api.susuApi, 'joinGroup').mockRejectedValue({
      code: 'SUSU_ALREADY_A_MEMBER',
    });
    render(<JoinSusuScreen />);

    await act(async () => {
      fireEvent.changeText(screen.getByTestId('hidden-code-input'), 'STSH1234');
    });

    await waitFor(() => {
      expect(screen.getByTestId('join-error')).toBeTruthy();
      expect(screen.getByText(/already a member/i)).toBeTruthy();
      expect(screen.getByTestId('view-group-btn')).toBeTruthy();
    });
  });

  it('auto-uppercases code input', () => {
    render(<JoinSusuScreen />);
    fireEvent.changeText(screen.getByTestId('hidden-code-input'), 'stsh1234');
  });
});
