import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react-native';
import { CreateSusuScreen } from '../../src/screens/susu/CreateSusuScreen';
import * as api from '../../src/api/susu';

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: jest.fn() }),
}));

describe('CreateSusuScreen', () => {
  afterEach(() => jest.restoreAllMocks());

  it('renders all form fields', () => {
    render(<CreateSusuScreen />);
    expect(screen.getByTestId('name-input')).toBeTruthy();
    expect(screen.getByTestId('contribution-input')).toBeTruthy();
    expect(screen.getByTestId('frequency-picker')).toBeTruthy();
    expect(screen.getByTestId('member-count-picker')).toBeTruthy();
  });

  it('shows inline error when name is empty on submit', async () => {
    render(<CreateSusuScreen />);
    fireEvent.press(screen.getByTestId('submit-btn'));
    await waitFor(() => {
      expect(screen.getByTestId('name-error')).toBeTruthy();
    });
  });

  it('shows inline error when contribution is zero', async () => {
    render(<CreateSusuScreen />);
    fireEvent.changeText(screen.getByTestId('name-input'), 'My Group');
    fireEvent.changeText(screen.getByTestId('contribution-input'), '0');
    fireEvent.press(screen.getByTestId('submit-btn'));
    await waitFor(() => {
      expect(screen.getByTestId('contribution-error')).toBeTruthy();
    });
  });

  it('frequency pills update selection', () => {
    render(<CreateSusuScreen />);
    fireEvent.press(screen.getByTestId('freq-WEEKLY'));
  });

  it('navigates to invite screen on success', async () => {
    const navigate = jest.fn();
    jest.spyOn(require('@react-navigation/native'), 'useNavigation').mockReturnValue({ navigate });
    jest.spyOn(api.susuApi, 'createGroup').mockResolvedValue({
      id: 'g1',
      join_code: 'STSH1234',
      status: 'PENDING',
    });

    render(<CreateSusuScreen />);
    fireEvent.changeText(screen.getByTestId('name-input'), 'Akua Circle');
    fireEvent.changeText(screen.getByTestId('contribution-input'), '200');
    fireEvent.press(screen.getByTestId('submit-btn'));

    await waitFor(() => {
      expect(navigate).toHaveBeenCalledWith(
        'CreateSusuInvite',
        expect.objectContaining({ joinCode: 'STSH1234' }),
      );
    });
  });

  it('shows free-tier limit Alert when API returns SUSU_FREE_TIER_LIMIT_REACHED', async () => {
    const alertSpy = jest.spyOn(require('react-native').Alert, 'alert');
    jest.spyOn(api.susuApi, 'createGroup').mockRejectedValue({
      code: 'SUSU_FREE_TIER_LIMIT_REACHED',
    });

    render(<CreateSusuScreen />);
    fireEvent.changeText(screen.getByTestId('name-input'), 'Second Group');
    fireEvent.changeText(screen.getByTestId('contribution-input'), '100');
    fireEvent.press(screen.getByTestId('submit-btn'));

    await waitFor(() => {
      expect(alertSpy).toHaveBeenCalledWith(
        'Group limit reached',
        expect.stringContaining('free plan'),
        expect.any(Array),
      );
    });
  });
});
