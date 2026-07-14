import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import UserDetailPage from '../src/routes/users/UserDetailPage';
import { AdminAuthProvider } from '../src/auth/AdminAuthContext';
import * as usersAdmin from '../src/api/usersAdmin';
import type { AdminUserDetailResponse } from '../src/api/usersAdmin';

vi.mock('../src/api/usersAdmin');

function renderPage(userId = 'user-1') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminAuthProvider>
        <MemoryRouter initialEntries={[`/users/${userId}`]}>
          <Routes>
            <Route path="/users/:userId" element={<UserDetailPage />} />
          </Routes>
        </MemoryRouter>
      </AdminAuthProvider>
    </QueryClientProvider>,
  );
}

const baseDetail: AdminUserDetailResponse = {
  user: {
    id: 'user-1',
    displayName: 'Akua Mensah',
    email: 'akua@pennyrise.app',
    phone: '+233241234567',
    kycStatus: 'APPROVED',
    accountStatus: 'ACTIVE',
    subscriptionTier: 'FREE',
    createdAt: '2026-01-01T00:00:00Z',
    maskedGhanaCard: 'GHA-*****7890-1',
  },
  vaults: [],
  activeSusuMemberships: [],
  recentTransactions: [],
  kycSubmissionHistory: [
    {
      submissionId: 'sub-1',
      status: 'APPROVED',
      reviewPath: 'AUTOMATED',
      decision: 'APPROVE',
      decisionReason: null,
      submittedAt: '2026-01-01T00:00:00Z',
      decidedAt: '2026-01-01T00:05:00Z',
      documents: [
        {
          documentType: 'FRONT_OF_CARD',
          signedUrl: 'https://storage.example/front.jpg',
          expiresAt: '2026-01-02T00:00:00Z',
        },
      ],
    },
  ],
};

describe('UserDetailPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders the Ghana Card as the pre-masked value from the API, showing only the last 4 digits', async () => {
    // The admin user endpoint never sends a raw Ghana Card number — only
    // `maskedGhanaCard` exists on AdminUserView/AdminUserListItem (verified
    // against the real DTOs), so masking is enforced server-side, not by
    // this page hiding a raw value it never receives.
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue(baseDetail);

    renderPage();

    const maskedRow = await screen.findByTestId('ghana-card-masked');
    expect(maskedRow.textContent).toContain('GHA-*****7890-1');
  });

  it('Suspend button is visible for an ACTIVE user, Restore is hidden', async () => {
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue(baseDetail);

    renderPage();

    const actionBar = await screen.findByTestId('action-bar');
    expect(actionBar).toHaveTextContent('Suspend');
    expect(actionBar).not.toHaveTextContent('Restore');
  });

  it('Restore button is visible for a SUSPENDED user, Suspend is hidden', async () => {
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue({
      ...baseDetail,
      user: { ...baseDetail.user, accountStatus: 'SUSPENDED' },
    });

    renderPage();

    const actionBar = await screen.findByTestId('action-bar');
    expect(actionBar).toHaveTextContent('Restore');
    expect(actionBar).not.toHaveTextContent('Suspend');
  });

  it('suspend modal requires a reason before the confirm button is enabled', async () => {
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue(baseDetail);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Suspend' }));

    const confirmButton = screen.getByRole('button', { name: /Suspend User/ });
    expect(confirmButton).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Suspension reason'), {
      target: { value: 'Repeated ToS violations' },
    });

    expect(confirmButton).not.toBeDisabled();
  });

  it('confirming suspend calls the API and refetches the user', async () => {
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue(baseDetail);
    vi.mocked(usersAdmin.suspendUser).mockResolvedValue(undefined);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Suspend' }));
    fireEvent.change(screen.getByLabelText('Suspension reason'), {
      target: { value: 'ToS violation' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Suspend User/ }));

    await waitFor(() =>
      expect(usersAdmin.suspendUser).toHaveBeenCalledWith('user-1', 'ToS violation'),
    );
  });

  it('confirming restore calls the API without requiring a reason', async () => {
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue({
      ...baseDetail,
      user: { ...baseDetail.user, accountStatus: 'SUSPENDED' },
    });
    vi.mocked(usersAdmin.restoreUser).mockResolvedValue(undefined);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Restore' }));
    fireEvent.click(screen.getByRole('button', { name: 'Restore User' }));

    await waitFor(() => expect(usersAdmin.restoreUser).toHaveBeenCalledWith('user-1'));
  });

  it('force logout is always visible regardless of account status', async () => {
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue(baseDetail);
    vi.mocked(usersAdmin.forceLogoutUser).mockResolvedValue(undefined);

    renderPage();

    fireEvent.click(await screen.findByRole('button', { name: 'Force Logout' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm Force Logout' }));

    await waitFor(() => expect(usersAdmin.forceLogoutUser).toHaveBeenCalledWith('user-1'));
  });

  it('KYC document links render when the response includes them', async () => {
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue(baseDetail);

    renderPage();

    await waitFor(() => expect(screen.getByTestId('kyc-documents')).toBeInTheDocument());
    expect(screen.getByText('FRONT_OF_CARD')).toBeInTheDocument();
  });

  it('shows a fallback message when a submission has no documents', async () => {
    vi.mocked(usersAdmin.fetchUserDetail).mockResolvedValue({
      ...baseDetail,
      kycSubmissionHistory: [{ ...baseDetail.kycSubmissionHistory[0], documents: undefined }],
    });

    renderPage();

    await screen.findByText(/No document images available/);
    expect(screen.queryByTestId('kyc-documents')).not.toBeInTheDocument();
  });
});
