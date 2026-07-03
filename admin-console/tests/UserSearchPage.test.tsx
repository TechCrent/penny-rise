import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import UserSearchPage from '../src/routes/users/UserSearchPage';
import { AdminAuthProvider } from '../src/auth/AdminAuthContext';
import * as usersAdmin from '../src/api/usersAdmin';
import type { AdminUserListResponse } from '../src/api/usersAdmin';

vi.mock('../src/api/usersAdmin');

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminAuthProvider>
        <MemoryRouter>
          <UserSearchPage />
        </MemoryRouter>
      </AdminAuthProvider>
    </QueryClientProvider>,
  );
}

const sampleResponse: AdminUserListResponse = {
  items: [
    {
      id: 'user-1',
      displayName: 'Akua Mensah',
      email: 'akua@stash.app',
      phone: '+233241234567',
      kycStatus: 'APPROVED',
      accountStatus: 'ACTIVE',
      subscriptionTier: 'FREE',
      createdAt: '2026-01-01T00:00:00Z',
      maskedGhanaCard: 'GHA-*****7890-1',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe('UserSearchPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders search results in a table', async () => {
    vi.mocked(usersAdmin.searchUsers).mockResolvedValue(sampleResponse);

    renderPage();

    expect(await screen.findByText('Akua Mensah')).toBeInTheDocument();
    expect(screen.getByText('akua@stash.app')).toBeInTheDocument();
  });

  it('typing in the search box triggers a re-fetch with the search term', async () => {
    vi.mocked(usersAdmin.searchUsers).mockResolvedValue({ ...sampleResponse, items: [] });

    renderPage();

    fireEvent.change(screen.getByLabelText('Search by email or phone'), {
      target: { value: 'akua@stash.app' },
    });

    await waitFor(() =>
      expect(usersAdmin.searchUsers).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'akua@stash.app' }),
      ),
    );
  });

  it('shows an empty state when no results match', async () => {
    vi.mocked(usersAdmin.searchUsers).mockResolvedValue({
      ...sampleResponse,
      items: [],
      totalElements: 0,
    });

    renderPage();

    expect(await screen.findByText('No users match this search.')).toBeInTheDocument();
  });

  it('selecting an account status filter re-fetches with that filter', async () => {
    vi.mocked(usersAdmin.searchUsers).mockResolvedValue(sampleResponse);

    renderPage();
    await screen.findByText('Akua Mensah');

    fireEvent.change(screen.getByLabelText('Filter by account status'), {
      target: { value: 'SUSPENDED' },
    });

    await waitFor(() =>
      expect(usersAdmin.searchUsers).toHaveBeenCalledWith(
        expect.objectContaining({ accountStatus: 'SUSPENDED' }),
      ),
    );
  });
});
