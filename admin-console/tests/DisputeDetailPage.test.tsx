import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import DisputeDetailPage from '../src/routes/disputes/DisputeDetailPage';
import { AdminAuthProvider } from '../src/auth/AdminAuthContext';
import * as disputesAdmin from '../src/api/disputesAdmin';
import type { AdminDisputeDetail } from '../src/api/disputesAdmin';

vi.mock('../src/api/disputesAdmin');

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminAuthProvider>
        <MemoryRouter initialEntries={['/disputes/d-1']}>
          <Routes>
            <Route path="/disputes/:disputeId" element={<DisputeDetailPage />} />
          </Routes>
        </MemoryRouter>
      </AdminAuthProvider>
    </QueryClientProvider>,
  );
}

const inReviewDispute: AdminDisputeDetail = {
  id: 'd-1',
  raisedByUserId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  disputeType: 'ACCOUNT',
  relatedEntityType: 'ACCOUNT',
  relatedEntityId: 'u-1',
  subject: 'Account issue',
  status: 'IN_REVIEW',
  priority: 'HIGH',
  assignedToAdminId: 'admin-1',
  createdAt: '2026-07-01T00:00:00Z',
  description: 'Full description of what went wrong.',
  assignmentHistory: [
    { adminId: 'admin-1', adminName: 'Kwame Admin', assignedAt: '2026-07-01T01:00:00Z' },
  ],
};

describe('DisputeDetailPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('resolve button is disabled until resolution text is entered', async () => {
    vi.mocked(disputesAdmin.fetchDisputeDetail).mockResolvedValue(inReviewDispute);

    renderPage();

    const resolveButton = await screen.findByText('Resolve Dispute');
    expect(resolveButton.closest('button')).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Resolution notes'), {
      target: { value: 'Refunded via RFD-001' },
    });

    expect(screen.getByText('Resolve Dispute').closest('button')).not.toBeDisabled();
  });

  it('submitting resolve calls the API with the resolution text', async () => {
    vi.mocked(disputesAdmin.fetchDisputeDetail).mockResolvedValue(inReviewDispute);
    vi.mocked(disputesAdmin.resolveDispute).mockResolvedValue(undefined);

    renderPage();

    fireEvent.change(await screen.findByLabelText('Resolution notes'), {
      target: { value: 'Refunded' },
    });
    fireEvent.click(screen.getByText('Resolve Dispute'));

    await waitFor(() =>
      expect(disputesAdmin.resolveDispute).toHaveBeenCalledWith('d-1', 'Refunded'),
    );
  });

  it('close-no-action button is disabled until a reason is entered', async () => {
    vi.mocked(disputesAdmin.fetchDisputeDetail).mockResolvedValue(inReviewDispute);

    renderPage();

    const closeButton = await screen.findByText('Close With No Action');
    expect(closeButton.closest('button')).toBeDisabled();

    fireEvent.change(screen.getByLabelText('Close reason'), {
      target: { value: 'No evidence of error' },
    });

    expect(screen.getByText('Close With No Action').closest('button')).not.toBeDisabled();
  });

  it('submitting close-no-action calls the API with the reason', async () => {
    vi.mocked(disputesAdmin.fetchDisputeDetail).mockResolvedValue(inReviewDispute);
    vi.mocked(disputesAdmin.closeDisputeNoAction).mockResolvedValue(undefined);

    renderPage();

    fireEvent.change(await screen.findByLabelText('Close reason'), {
      target: { value: 'No evidence' },
    });
    fireEvent.click(screen.getByText('Close With No Action'));

    await waitFor(() =>
      expect(disputesAdmin.closeDisputeNoAction).toHaveBeenCalledWith('d-1', 'No evidence'),
    );
  });

  it('resolve/close forms are hidden for a dispute still in OPEN (not yet assigned)', async () => {
    vi.mocked(disputesAdmin.fetchDisputeDetail).mockResolvedValue({
      ...inReviewDispute,
      status: 'OPEN',
    });

    renderPage();

    await screen.findByText('Full description of what went wrong.');
    expect(screen.queryByLabelText('Resolution notes')).not.toBeInTheDocument();
    expect(screen.getByText(/must be assigned before/)).toBeInTheDocument();
  });

  it('renders the assignment history', async () => {
    vi.mocked(disputesAdmin.fetchDisputeDetail).mockResolvedValue(inReviewDispute);

    renderPage();

    expect(await screen.findByText('Kwame Admin')).toBeInTheDocument();
  });

  it('renders a related-entity link for ACCOUNT type (resolves to the user detail page)', async () => {
    vi.mocked(disputesAdmin.fetchDisputeDetail).mockResolvedValue(inReviewDispute);

    renderPage();

    const link = await screen.findByTestId('related-entity-link');
    expect(link.getAttribute('href')).toBe('/users/u-1');
  });

  it('renders a fallback message for related_entity_type with no admin page yet (e.g. TRANSACTION)', async () => {
    vi.mocked(disputesAdmin.fetchDisputeDetail).mockResolvedValue({
      ...inReviewDispute,
      relatedEntityType: 'TRANSACTION',
    });

    renderPage();

    await screen.findByText('Full description of what went wrong.');
    expect(screen.queryByTestId('related-entity-link')).not.toBeInTheDocument();
    expect(screen.getByText(/No admin detail view available/)).toBeInTheDocument();
  });
});
