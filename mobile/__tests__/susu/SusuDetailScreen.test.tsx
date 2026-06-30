import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { SusuDetailScreen } from '../../src/screens/susu/SusuDetailScreen';
import * as hooks from '../../src/hooks/useSusuDetail';
import type { SusuGroupDetailResponse } from '../../src/types/susu';

jest.mock('@react-navigation/native', () => ({
  useRoute:      () => ({ params: { groupId: 'group-1' } }),
  useNavigation: () => ({ navigate: jest.fn() }),
}));

const activeMember = {
  user_id: 'u1', display_name: 'Akua Mensah', rotation_position: 1,
  membership_status: 'ACTIVE', joined_at: '2026-06-24T00:00:00Z', is_organiser: true,
};

const baseGroup: SusuGroupDetailResponse = {
  id: 'group-1', name: "Akua's Circle", status: 'ACTIVE',
  organiser_user_id: 'u1', is_caller_organiser: false,
  contribution_amount: 20000, contribution_amount_cedis: '200.00',
  frequency: 'MONTHLY', target_member_count: 6,
  join_code: 'STSH1234', start_date: '2026-06-24',
  created_at: '2026-06-24T00:00:00Z',
  current_round: {
    id: 'round-1', round_number: 1, total_rounds: 6,
    status: 'COLLECTING',
    recipient_user_id: 'u1', recipient_display_name: 'Akua Mensah',
    scheduled_collection_at: '2026-07-24T00:00:00Z',
    expected_pot_amount: 120000, expected_pot_amount_cedis: '1,200.00',
    actual_pot_amount: null,
    contributions: [
      { member_user_id: 'u1', display_name: 'Akua',  status: 'PAID',    is_late: false, penalty_amount: 0, paid_at: '2026-06-25T00:00:00Z' },
      { member_user_id: 'u2', display_name: 'Kwame', status: 'PENDING', is_late: false, penalty_amount: 0, paid_at: null },
    ],
  },
  members: [
    activeMember,
    { ...activeMember, user_id: 'u2', display_name: 'Kwame Asante', rotation_position: 2, is_organiser: false },
  ],
  caller_membership: { id: 'mem-1', rotation_position: 2, status: 'ACTIVE', joined_at: '2026-06-24T00:00:00Z' },
};

function mockHook(overrides: Partial<ReturnType<typeof hooks.useSusuDetail>> = {}) {
  jest.spyOn(hooks, 'useSusuDetail').mockReturnValue({
    group: baseGroup, loading: false, refreshing: false, error: null,
    fetch: jest.fn(), refresh: jest.fn(),
    ...overrides,
  });
}

describe('SusuDetailScreen', () => {
  afterEach(() => jest.restoreAllMocks());

  it('renders round hero for ACTIVE group', () => {
    mockHook();
    render(<SusuDetailScreen />);
    expect(screen.getByTestId('round-hero')).toBeTruthy();
    expect(screen.getByText('Akua Mensah')).toBeTruthy();
  });

  it('shows contribution statuses', () => {
    mockHook();
    render(<SusuDetailScreen />);
    expect(screen.getByTestId('contributions-list')).toBeTruthy();
    // display_name 'Akua' / 'Kwame' also appear as ring labels — use getAllByText
    expect(screen.getAllByText('Akua').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Kwame').length).toBeGreaterThanOrEqual(1);
  });

  it('shows PAID and PENDING pills correctly', () => {
    mockHook();
    render(<SusuDetailScreen />);
    expect(screen.getByText('Paid')).toBeTruthy();
    expect(screen.getByText('Pending')).toBeTruthy();
  });

  it('shows Activate CTA for organiser when member count met', () => {
    const organiserGroup: SusuGroupDetailResponse = {
      ...baseGroup,
      status: 'PENDING',
      is_caller_organiser: true,
      members: Array(6).fill(0).map((_, i) => ({
        ...activeMember, user_id: `u${i}`, rotation_position: null,
      })),
      current_round: null,
    };
    mockHook({ group: organiserGroup });
    render(<SusuDetailScreen />);
    expect(screen.getByTestId('activate-btn')).toBeTruthy();
  });

  it('hides Activate CTA for non-organiser', () => {
    const memberGroup: SusuGroupDetailResponse = {
      ...baseGroup, status: 'PENDING', is_caller_organiser: false, current_round: null,
    };
    mockHook({ group: memberGroup });
    render(<SusuDetailScreen />);
    expect(screen.queryByTestId('activate-btn')).toBeNull();
  });

  it('shows disbursing badge when round is DISBURSING', () => {
    const disbursingGroup: SusuGroupDetailResponse = {
      ...baseGroup,
      current_round: { ...baseGroup.current_round!, status: 'DISBURSING' },
    };
    mockHook({ group: disbursingGroup });
    render(<SusuDetailScreen />);
    expect(screen.getByTestId('disbursing-badge')).toBeTruthy();
  });

  it('shows completed hero for COMPLETED group', () => {
    const completedGroup: SusuGroupDetailResponse = {
      ...baseGroup, status: 'COMPLETED', current_round: null,
    };
    mockHook({ group: completedGroup });
    render(<SusuDetailScreen />);
    expect(screen.getByTestId('completed-hero')).toBeTruthy();
  });

  it('shows error state with retry', () => {
    mockHook({ group: null, error: 'Network error' });
    render(<SusuDetailScreen />);
    expect(screen.getByText('Network error')).toBeTruthy();
    expect(screen.getByText('Retry')).toBeTruthy();
  });

  it('shows loading indicator on initial load', () => {
    mockHook({ group: null, loading: true });
    render(<SusuDetailScreen />);
    expect(screen.queryByTestId('round-hero')).toBeNull();
  });
});
