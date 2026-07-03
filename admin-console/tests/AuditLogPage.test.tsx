import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import AuditLogPage from '../src/routes/audit-log/AuditLogPage';
import { AdminAuthProvider } from '../src/auth/AdminAuthContext';
import * as auditLogAdmin from '../src/api/auditLogAdmin';
import type { AuditLogEntry } from '../src/api/auditLogAdmin';

vi.mock('../src/api/auditLogAdmin');

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminAuthProvider>
        <MemoryRouter>
          <AuditLogPage />
        </MemoryRouter>
      </AdminAuthProvider>
    </QueryClientProvider>,
  );
}

const suspendEntry: AuditLogEntry = {
  eventId: 'evt-1',
  eventType: 'UserSuspendedEvent',
  actorType: 'ADMIN',
  actorId: 'admin-1',
  targetEntityType: 'USER',
  targetEntityId: 'user-1',
  payload: { userId: 'user-1', adminAccountId: 'admin-1', reason: 'ToS violation' },
  occurredAt: '2026-07-01T09:00:00Z',
};

describe('AuditLogPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders audit entries from the API', async () => {
    vi.mocked(auditLogAdmin.fetchAuditLogFacets).mockResolvedValue({
      eventTypes: ['UserSuspendedEvent'],
      targetEntityTypes: ['USER'],
    });
    vi.mocked(auditLogAdmin.fetchAuditLog).mockResolvedValue({
      entries: [suspendEntry],
      nextCursor: null,
      hasMore: false,
    });

    renderPage();

    const row = await screen.findByTestId('audit-row-evt-1');
    expect(row).toBeInTheDocument();
    expect(row).toHaveTextContent('UserSuspendedEvent');
  });

  it('changing the actor_id filter re-fetches with the new value', async () => {
    vi.mocked(auditLogAdmin.fetchAuditLogFacets).mockResolvedValue({
      eventTypes: [],
      targetEntityTypes: [],
    });
    vi.mocked(auditLogAdmin.fetchAuditLog).mockResolvedValue({
      entries: [],
      nextCursor: null,
      hasMore: false,
    });

    renderPage();
    await screen.findByLabelText('Filter by actor ID');

    fireEvent.change(screen.getByLabelText('Filter by actor ID'), { target: { value: 'admin-1' } });

    await waitFor(() =>
      expect(auditLogAdmin.fetchAuditLog).toHaveBeenCalledWith(
        expect.objectContaining({ actorId: 'admin-1' }),
      ),
    );
  });

  it('clicking a row expands the inline payload panel with formatted JSON', async () => {
    vi.mocked(auditLogAdmin.fetchAuditLogFacets).mockResolvedValue({
      eventTypes: [],
      targetEntityTypes: [],
    });
    vi.mocked(auditLogAdmin.fetchAuditLog).mockResolvedValue({
      entries: [suspendEntry],
      nextCursor: null,
      hasMore: false,
    });

    renderPage();

    fireEvent.click(await screen.findByTestId('audit-row-evt-1'));

    const payloadPanel = await screen.findByTestId('audit-payload-evt-1');
    expect(payloadPanel.textContent).toContain('"reason"');
    expect(payloadPanel.textContent).toContain('"ToS violation"');
  });

  it('a USER-target entry renders as a deep link to the user detail page', async () => {
    vi.mocked(auditLogAdmin.fetchAuditLogFacets).mockResolvedValue({
      eventTypes: [],
      targetEntityTypes: [],
    });
    vi.mocked(auditLogAdmin.fetchAuditLog).mockResolvedValue({
      entries: [suspendEntry],
      nextCursor: null,
      hasMore: false,
    });

    renderPage();

    const link = await screen.findByTestId('audit-entity-link-evt-1');
    expect(link.getAttribute('href')).toBe('/users/user-1');
  });

  it('a TRANSACTION-target entry renders plain text, not a broken link', async () => {
    vi.mocked(auditLogAdmin.fetchAuditLogFacets).mockResolvedValue({
      eventTypes: [],
      targetEntityTypes: [],
    });
    vi.mocked(auditLogAdmin.fetchAuditLog).mockResolvedValue({
      entries: [
        { ...suspendEntry, eventId: 'evt-2', targetEntityType: 'TRANSACTION', targetEntityId: 'txn-1' },
      ],
      nextCursor: null,
      hasMore: false,
    });

    renderPage();

    await screen.findByTestId('audit-row-evt-2');
    expect(screen.queryByTestId('audit-entity-link-evt-2')).not.toBeInTheDocument();
    expect(screen.getByText(/TRANSACTION.*txn-1/)).toBeInTheDocument();
  });

  it('shows an empty state when no entries match the filters', async () => {
    vi.mocked(auditLogAdmin.fetchAuditLogFacets).mockResolvedValue({
      eventTypes: [],
      targetEntityTypes: [],
    });
    vi.mocked(auditLogAdmin.fetchAuditLog).mockResolvedValue({
      entries: [],
      nextCursor: null,
      hasMore: false,
    });

    renderPage();

    expect(await screen.findByTestId('audit-log-empty-state')).toBeInTheDocument();
  });

  it('load more triggers the next page fetch with the cursor', async () => {
    vi.mocked(auditLogAdmin.fetchAuditLogFacets).mockResolvedValue({
      eventTypes: [],
      targetEntityTypes: [],
    });
    vi.mocked(auditLogAdmin.fetchAuditLog)
      .mockResolvedValueOnce({ entries: [suspendEntry], nextCursor: 'evt-1', hasMore: true })
      .mockResolvedValueOnce({
        entries: [{ ...suspendEntry, eventId: 'evt-2' }],
        nextCursor: null,
        hasMore: false,
      });

    renderPage();
    await screen.findByTestId('audit-row-evt-1');

    fireEvent.click(screen.getByText('Load more'));

    await waitFor(() => expect(screen.getByTestId('audit-row-evt-2')).toBeInTheDocument());
    expect(auditLogAdmin.fetchAuditLog).toHaveBeenLastCalledWith(
      expect.objectContaining({ cursor: 'evt-1' }),
    );
  });
});
