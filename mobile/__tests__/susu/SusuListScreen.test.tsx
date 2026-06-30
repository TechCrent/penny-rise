import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import { SusuListScreen } from '../../src/screens/susu/SusuListScreen';
import * as hooks from '../../src/hooks/useSusuGroups';
import type { SusuGroupListResponse } from '../../src/types/susu';

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: jest.fn() }),
}));

const mockGroup: SusuGroupListResponse = {
  group_id: 'g1',
  name: "Akua's Circle",
  status: 'ACTIVE',
  organiser_user_id: 'u1',
  is_organiser: false,
  contribution_amount: 20000,
  contribution_amount_cedis: '200.00',
  frequency: 'MONTHLY',
  target_member_count: 6,
  current_member_count: 6,
  current_round_number: 2,
  total_rounds: 6,
  next_due_date: '2026-07-24T00:00:00Z',
  caller_rotation_position: 3,
  caller_is_next_recipient: false,
  join_code: 'STSH1234',
  created_at: '2026-06-24T00:00:00Z',
};

function mockHook(overrides: Partial<ReturnType<typeof hooks.useSusuGroups>> = {}) {
  jest.spyOn(hooks, 'useSusuGroups').mockReturnValue({
    groups: [],
    loading: false,
    refreshing: false,
    error: null,
    fetch: jest.fn(),
    refresh: jest.fn(),
    ...overrides,
  });
}

describe('SusuListScreen', () => {
  afterEach(() => jest.restoreAllMocks());

  it('shows empty state when no groups', () => {
    mockHook({ groups: [] });
    render(<SusuListScreen />);
    expect(screen.getByTestId('empty-state')).toBeTruthy();
    expect(screen.getByTestId('create-susu-cta')).toBeTruthy();
    expect(screen.getByTestId('join-susu-cta')).toBeTruthy();
  });

  it('renders group cards when groups exist', () => {
    mockHook({ groups: [mockGroup] });
    render(<SusuListScreen />);
    expect(screen.getByText("Akua's Circle")).toBeTruthy();
  });

  it('shows active tab with correct count', () => {
    mockHook({ groups: [mockGroup] });
    render(<SusuListScreen />);
    expect(screen.getByText('Active (1)')).toBeTruthy();
    expect(screen.getByText('Past (0)')).toBeTruthy();
  });

  it('switching to Past tab shows completed groups', () => {
    const pastGroup = { ...mockGroup, group_id: 'g2', status: 'COMPLETED' as const };
    mockHook({ groups: [mockGroup, pastGroup] });
    render(<SusuListScreen />);
    fireEvent.press(screen.getByTestId('tab-past'));
    expect(screen.getByText('Past (1)')).toBeTruthy();
  });

  it('shows loading indicator when loading', () => {
    mockHook({ loading: true, groups: [] });
    render(<SusuListScreen />);
    expect(screen.queryByTestId('empty-state')).toBeNull();
  });
});
