import React from 'react';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react-native';
import { RecipientPickerScreen } from '../../src/screens/transfer/RecipientPickerScreen';
import * as api from '../../src/api/transfers';

jest.mock('@react-navigation/native', () => ({
  useNavigation: () => ({ navigate: jest.fn() }),
}));

const mockRecipients = [
  { id: 'u1', displayName: 'Kofi Mensah', email: 'kofi@example.com' },
  { id: 'u2', displayName: 'Ama Serwah', email: 'ama@example.com' },
];

describe('RecipientPickerScreen', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('renders search input', () => {
    render(<RecipientPickerScreen />);
    expect(screen.getByTestId('search-input')).toBeTruthy();
  });

  it('does not call API when query is shorter than 2 characters', async () => {
    jest.spyOn(api.transferApi, 'searchRecipients').mockResolvedValue(mockRecipients);
    render(<RecipientPickerScreen />);
    fireEvent.changeText(screen.getByTestId('search-input'), 'K');
    act(() => {
      jest.runAllTimers();
    });
    expect(api.transferApi.searchRecipients).not.toHaveBeenCalled();
  });

  it('shows results after debounced search', async () => {
    jest.spyOn(api.transferApi, 'searchRecipients').mockResolvedValue(mockRecipients);
    render(<RecipientPickerScreen />);
    fireEvent.changeText(screen.getByTestId('search-input'), 'Ko');
    act(() => {
      jest.runAllTimers();
    });
    await waitFor(() => {
      expect(screen.getByTestId('recipient-u1')).toBeTruthy();
      expect(screen.getByTestId('recipient-u2')).toBeTruthy();
    });
    expect(api.transferApi.searchRecipients).toHaveBeenCalledWith('Ko');
  });

  it('navigates to SendMoney when a recipient is tapped', async () => {
    const navigate = jest.fn();
    jest.spyOn(require('@react-navigation/native'), 'useNavigation').mockReturnValue({ navigate });
    jest.spyOn(api.transferApi, 'searchRecipients').mockResolvedValue(mockRecipients);
    render(<RecipientPickerScreen />);
    fireEvent.changeText(screen.getByTestId('search-input'), 'Ko');
    act(() => {
      jest.runAllTimers();
    });
    await waitFor(() => {
      expect(screen.getByTestId('recipient-u1')).toBeTruthy();
    });
    fireEvent.press(screen.getByTestId('recipient-u1'));
    expect(navigate).toHaveBeenCalledWith('SendMoney', { recipient: mockRecipients[0] });
  });
});
