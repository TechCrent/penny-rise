import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import DisputeQueuePage from '../src/routes/disputes/DisputeQueuePage';
import { AdminAuthProvider } from '../src/auth/AdminAuthContext';
import * as disputesAdmin from '../src/api/disputesAdmin';
import type { AdminDisputeListItem } from '../src/api/disputesAdmin';

vi.mock('../src/api/disputesAdmin');

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminAuthProvider>
        <MemoryRouter>
          <DisputeQueuePage />
        </MemoryRouter>
      </AdminAuthProvider>
    </QueryClientProvider>,
  );
}

const baseDispute: AdminDisputeListItem = {
  id: 'd-1',
  raisedByUserId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  disputeType: 'TRANSACTION',
  relatedEntityType: 'TRANSACTION',
  relatedEntityId: 't-1',
  subject: 'Charged twice for one deposit',
  status: 'OPEN',
  priority: 'CRITICAL',
  assignedToAdminId: null,
  createdAt: new Date(Date.now() - 3 * 3_600_000).toISOString(),
};

describe('DisputeQueuePage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders CRITICAL priority with red styling (URGENT does not exist in the schema)', async () => {
    vi.mocked(disputesAdmin.fetchDisputeQueue).mockResolvedValue({
      disputes: [baseDispute],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderPage();

    const badge = await screen.findByTestId('priority-badge-CRITICAL');
    expect(badge.className).toContain('bg-red-100');
    expect(badge.textContent).toBe('CRITICAL');
  });

  it('shows raised_by as a truncated user ID (no display name/email join exists)', async () => {
    vi.mocked(disputesAdmin.fetchDisputeQueue).mockResolvedValue({
      disputes: [baseDispute],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderPage();

    expect(await screen.findByText('aaaaaaaa…')).toBeInTheDocument();
  });

  it('clicking Assign to Me calls the assign endpoint and the row updates to IN_REVIEW inline', async () => {
    vi.mocked(disputesAdmin.fetchDisputeQueue).mockResolvedValue({
      disputes: [baseDispute],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(disputesAdmin.assignDispute).mockResolvedValue(undefined);

    renderPage();

    fireEvent.click(await screen.findByText('Assign to Me'));

    await waitFor(() =>
      expect(disputesAdmin.assignDispute).toHaveBeenCalledWith('d-1', expect.anything()),
    );
    await waitFor(() =>
      expect(screen.getByTestId('dispute-row-d-1')).toHaveTextContent('IN_REVIEW'),
    );
  });

  it('RESOLVED disputes do not appear in the default (unfiltered) view', async () => {
    vi.mocked(disputesAdmin.fetchDisputeQueue).mockResolvedValue({
      disputes: [
        baseDispute,
        { ...baseDispute, id: 'd-2', status: 'RESOLVED', subject: 'Old resolved one' },
      ],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    });

    renderPage();

    await screen.findByText('Charged twice for one deposit');
    expect(screen.queryByText('Old resolved one')).not.toBeInTheDocument();
  });

  it('RESOLVED disputes ARE shown when explicitly filtered by status=RESOLVED', async () => {
    vi.mocked(disputesAdmin.fetchDisputeQueue).mockResolvedValue({
      disputes: [{ ...baseDispute, id: 'd-2', status: 'RESOLVED', subject: 'Old resolved one' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });

    renderPage();

    fireEvent.change(screen.getByLabelText('Filter by status'), {
      target: { value: 'RESOLVED' },
    });

    await waitFor(() =>
      expect(disputesAdmin.fetchDisputeQueue).toHaveBeenCalledWith(
        expect.objectContaining({ status: 'RESOLVED' }),
      ),
    );
    expect(await screen.findByText('Old resolved one')).toBeInTheDocument();
  });
});
