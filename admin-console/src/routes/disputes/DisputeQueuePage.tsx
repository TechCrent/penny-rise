import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AdminShell } from '../../components/layout/AdminShell';
import { Button } from '@/components/ui/button';
import { DisputesTable } from './DisputesTable';
import { useDisputeQueue } from './useDisputeQueue';

const STATUS_OPTIONS = ['OPEN', 'IN_REVIEW', 'RESOLVED', 'CLOSED_NO_ACTION'];

export default function DisputeQueuePage() {
  const navigate = useNavigate();
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);

  const { queueQuery, assign, assigningId, isAssigning } = useDisputeQueue({
    status: statusFilter || undefined,
    page,
  });

  const disputes = queueQuery.data?.disputes ?? [];
  // Safety net for the AC's "resolved disputes disappear from the default
  // queue view" — applied client-side since it's unconfirmed whether the
  // backend's own unfiltered GET already excludes terminal statuses by
  // default. A no-op if it does; the actual enforcement if it doesn't.
  const visibleDisputes = statusFilter
    ? disputes
    : disputes.filter((d) => d.status !== 'RESOLVED' && d.status !== 'CLOSED_NO_ACTION');

  return (
    <AdminShell title="Dispute Queue">
      <select
        aria-label="Filter by status"
        className="h-8 rounded-lg border border-input bg-transparent px-2.5 text-sm mb-4"
        value={statusFilter}
        onChange={(e) => {
          setStatusFilter(e.target.value);
          setPage(0);
        }}
      >
        <option value="">All open work</option>
        {STATUS_OPTIONS.map((s) => (
          <option key={s} value={s}>
            {s}
          </option>
        ))}
      </select>

      {queueQuery.isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700 mb-4">
          Couldn't load the dispute queue.
        </div>
      )}

      {queueQuery.isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading…</div>
      ) : (
        <DisputesTable
          items={visibleDisputes}
          onSelect={(id) => navigate(`/disputes/${id}`)}
          onAssign={assign}
          assigningId={assigningId}
          isAssigning={isAssigning}
        />
      )}

      {queueQuery.data && queueQuery.data.totalPages > 1 && (
        <div className="flex justify-between items-center mt-4 text-sm text-slate-500">
          <span>
            Page {queueQuery.data.page + 1} of {queueQuery.data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={queueQuery.data.page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={queueQuery.data.page + 1 >= queueQuery.data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
