import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import FlaggedSusuGroupsPage from '../src/routes/susu-groups/FlaggedSusuGroupsPage';
import { AdminAuthProvider } from '../src/auth/AdminAuthContext';
import * as susuGroupsAdmin from '../src/api/susuGroupsAdmin';
import type { FlaggedSusuGroupListItem } from '../src/api/susuGroupsAdmin';

vi.mock('../src/api/susuGroupsAdmin');

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminAuthProvider>
        <MemoryRouter>
          <FlaggedSusuGroupsPage />
        </MemoryRouter>
      </AdminAuthProvider>
    </QueryClientProvider>,
  );
}

const flaggedGroup: FlaggedSusuGroupListItem = {
  id: 'group-1',
  name: 'Legon Roommates Susu',
  organiserUserId: 'user-1',
  flaggedAt: '2026-07-01T09:00:00Z',
  lastShortfallRoundNumber: 3,
  lastShortfallMemberUserId: 'user-2',
  lastShortfallAt: '2026-07-01T08:55:00Z',
  potBalancePesewas: 150000,
};

describe('FlaggedSusuGroupsPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders flagged groups with shortfall details and pot balance', async () => {
    vi.mocked(susuGroupsAdmin.fetchFlaggedSusuGroups).mockResolvedValue({
      items: [flaggedGroup],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderPage();

    expect(await screen.findByText('Legon Roommates Susu')).toBeInTheDocument();
    expect(screen.getByText(/Round 3 — penalty waived/)).toBeInTheDocument();
    expect(screen.getByText('GHS 1500.00')).toBeInTheDocument();
  });

  it('shows an empty state when no groups are flagged', async () => {
    vi.mocked(susuGroupsAdmin.fetchFlaggedSusuGroups).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    renderPage();

    expect(await screen.findByTestId('flagged-susu-empty-state')).toBeInTheDocument();
  });

  it('the organiser link points at the user detail page', async () => {
    vi.mocked(susuGroupsAdmin.fetchFlaggedSusuGroups).mockResolvedValue({
      items: [flaggedGroup],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderPage();

    const link = await screen.findByTestId('organiser-link-group-1');
    expect(link.getAttribute('href')).toBe('/users/user-1');
  });

  it('clicking Clear Flag calls the API and the row disappears once the list refetches', async () => {
    vi.mocked(susuGroupsAdmin.fetchFlaggedSusuGroups)
      .mockResolvedValueOnce({
        items: [flaggedGroup],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      })
      .mockResolvedValueOnce({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    vi.mocked(susuGroupsAdmin.clearSusuGroupFlag).mockResolvedValue(undefined);

    renderPage();
    await screen.findByTestId('flagged-susu-row-group-1');

    fireEvent.click(screen.getByText('Clear Flag'));

    await waitFor(() => expect(susuGroupsAdmin.clearSusuGroupFlag).toHaveBeenCalledWith('group-1'));
    await waitFor(() => expect(susuGroupsAdmin.fetchFlaggedSusuGroups).toHaveBeenCalledTimes(2));
  });

  it('a group with no identified shortfall contribution shows a dash, not an error', async () => {
    vi.mocked(susuGroupsAdmin.fetchFlaggedSusuGroups).mockResolvedValue({
      items: [{ ...flaggedGroup, lastShortfallRoundNumber: null, lastShortfallMemberUserId: null }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderPage();

    await screen.findByTestId('flagged-susu-row-group-1');
    expect(screen.getByText('—')).toBeInTheDocument();
  });
});
