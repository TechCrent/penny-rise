import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import KycQueuePage from '../src/routes/kyc-queue/KycQueuePage';
import { AdminAuthProvider } from '../src/auth/AdminAuthContext';
import * as kycAdmin from '../src/api/kycAdmin';
import * as usersAdmin from '../src/api/usersAdmin';
import type { AdminUserListResponse } from '../src/api/usersAdmin';

vi.mock('../src/api/kycAdmin');
vi.mock('../src/api/usersAdmin');

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminAuthProvider>
        <MemoryRouter>
          <KycQueuePage />
        </MemoryRouter>
      </AdminAuthProvider>
    </QueryClientProvider>,
  );
}

const flaggedUser: AdminUserListResponse = {
  items: [
    {
      id: 'user-1',
      displayName: 'Kwame Asante',
      email: 'kwame@stash.app',
      phone: '+233241234567',
      kycStatus: 'RESUBMISSION_REQUIRED',
      accountStatus: 'ACTIVE',
      subscriptionTier: 'FREE',
      createdAt: '2026-01-01T00:00:00Z',
      maskedGhanaCard: 'GHA-*****7890-1',
    },
  ],
  page: 0,
  size: 100,
  totalElements: 1,
  totalPages: 1,
};

describe('KycQueuePage — Flagged Accounts tab (v0.5-035)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(kycAdmin.fetchQueue).mockResolvedValue({ items: [], nextCursor: null, hasMore: false });
  });

  it('defaults to the Review Queue tab', async () => {
    renderPage();

    expect(await screen.findByText('0 submission(s) awaiting review')).toBeInTheDocument();
    expect(usersAdmin.searchUsers).not.toHaveBeenCalled();
  });

  it('switching to Flagged Accounts fetches users filtered by kyc_status=RESUBMISSION_REQUIRED', async () => {
    vi.mocked(usersAdmin.searchUsers).mockResolvedValue(flaggedUser);

    renderPage();
    fireEvent.click(await screen.findByTestId('flagged-accounts-tab'));

    expect(await screen.findByText('Kwame Asante')).toBeInTheDocument();
    expect(usersAdmin.searchUsers).toHaveBeenCalledWith(
      expect.objectContaining({ kycStatus: 'RESUBMISSION_REQUIRED' }),
    );
  });

  it('a flagged account row links to the user detail page', async () => {
    vi.mocked(usersAdmin.searchUsers).mockResolvedValue(flaggedUser);

    renderPage();
    fireEvent.click(await screen.findByTestId('flagged-accounts-tab'));

    const row = await screen.findByTestId('user-row-user-1');
    expect(row).toBeInTheDocument();
  });

  it('shows an empty state when no accounts are flagged', async () => {
    vi.mocked(usersAdmin.searchUsers).mockResolvedValue({
      items: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    });

    renderPage();
    fireEvent.click(await screen.findByTestId('flagged-accounts-tab'));

    expect(await screen.findByText('No users match this search.')).toBeInTheDocument();
  });
});
