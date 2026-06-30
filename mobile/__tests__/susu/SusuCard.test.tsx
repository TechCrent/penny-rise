import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { SusuCard } from '../../src/components/susu/SusuCard';
import type { SusuGroupListResponse } from '../../src/types/susu';

const baseGroup: SusuGroupListResponse = {
  group_id: 'group-1',
  name: "Akua's Circle",
  status: 'ACTIVE',
  organiser_user_id: 'user-1',
  is_organiser: false,
  contribution_amount: 20000,
  contribution_amount_cedis: '200.00',
  frequency: 'MONTHLY',
  target_member_count: 6,
  current_member_count: 6,
  current_round_number: 1,
  total_rounds: 6,
  next_due_date: '2026-07-24T00:00:00Z',
  caller_rotation_position: 3,
  caller_is_next_recipient: false,
  join_code: 'STSH1234',
  created_at: '2026-06-24T00:00:00Z',
};

describe('SusuCard', () => {
  it('renders group name', () => {
    render(<SusuCard group={baseGroup} onPress={() => {}} />);
    expect(screen.getByText("Akua's Circle")).toBeTruthy();
  });

  it('shows round number and total', () => {
    render(<SusuCard group={baseGroup} onPress={() => {}} />);
    expect(screen.getByText('Round 1 of 6')).toBeTruthy();
  });

  it('shows caller rotation position', () => {
    render(<SusuCard group={baseGroup} onPress={() => {}} />);
    expect(screen.getByText('Your position: #3')).toBeTruthy();
  });

  it('shows "You\'re next" pill when caller_is_next_recipient is true', () => {
    const group = { ...baseGroup, caller_is_next_recipient: true };
    render(<SusuCard group={group} onPress={() => {}} />);
    expect(screen.getByTestId('youre-next-pill')).toBeTruthy();
  });

  it('hides "You\'re next" pill when not next recipient', () => {
    render(<SusuCard group={baseGroup} onPress={() => {}} />);
    expect(screen.queryByTestId('youre-next-pill')).toBeNull();
  });

  it('shows "Waiting to start" for PENDING groups', () => {
    const group = {
      ...baseGroup,
      status: 'PENDING' as const,
      current_round_number: null,
    };
    render(<SusuCard group={group} onPress={() => {}} />);
    expect(screen.getByText('Waiting to start')).toBeTruthy();
  });

  it('shows contribution amount and frequency', () => {
    render(<SusuCard group={baseGroup} onPress={() => {}} />);
    expect(screen.getByText('200.00 · Monthly')).toBeTruthy();
  });
});
