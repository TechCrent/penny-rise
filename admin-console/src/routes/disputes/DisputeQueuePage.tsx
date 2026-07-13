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
  const [bulkNotes, setBulkNotes] = useState('');

  const {
    queueQuery,
    assign,
    assigningId,
    isAssigning,
    selectedIds,
    toggleSelect,
    toggleSelectAllResolvable,
    bulkResolveMutation,
  } = useDisputeQueue({
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

  const handleBulkResolve = () => {
    bulkResolveMutation.mutate(
      { ids: Array.from(selectedIds), notes: bulkNotes },
      { onSuccess: () => setBulkNotes('') },
    );
  };

  return (
    <AdminShell title="Dispute Queue">
      <select
        aria-label="Filter by status"
        className="mb-4 h-8 rounded-lg border border-input bg-card-secondary px-2.5 text-sm text-foreground"
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
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Couldn't load the dispute queue.
        </div>
      )}

      {selectedIds.size > 0 && (
        <div className="mb-4 flex items-center gap-3 rounded-lg bg-card-secondary px-4 py-2">
          <span className="text-sm whitespace-nowrap text-muted-foreground">
            {selectedIds.size} selected
          </span>
          <input
            className="h-8 flex-1 rounded-lg border border-input bg-card px-2.5 text-sm text-foreground"
            placeholder="Resolution notes applied to all selected disputes"
            value={bulkNotes}
            onChange={(e) => setBulkNotes(e.target.value)}
          />
          <Button
            size="sm"
            disabled={!bulkNotes.trim() || bulkResolveMutation.isPending}
            onClick={handleBulkResolve}
          >
            {bulkResolveMutation.isPending
              ? 'Resolving…'
              : `Resolve Selected (${selectedIds.size})`}
          </Button>
        </div>
      )}

      {queueQuery.isLoading ? (
        <div className="py-12 text-center text-muted-foreground">Loading…</div>
      ) : (
        <DisputesTable
          items={visibleDisputes}
          onSelect={(id) => navigate(`/disputes/${id}`)}
          onAssign={assign}
          assigningId={assigningId}
          isAssigning={isAssigning}
          selectedIds={selectedIds}
          onToggleSelect={toggleSelect}
          onToggleSelectAllResolvable={toggleSelectAllResolvable}
        />
      )}

      {queueQuery.data && queueQuery.data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
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
