import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AdminShell } from '../../components/layout/AdminShell';
import { Button } from '@/components/ui/button';
import { QueueTable } from './QueueTable';
import { ReviewModal } from './ReviewModal';
import { useKycQueue } from './useKycQueue';
import { useFlaggedKycAccounts } from './useFlaggedKycAccounts';
import { UsersTable } from '../users/UsersTable';

type Tab = 'queue' | 'flagged';

export default function KycQueuePage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('queue');

  const {
    queueQuery,
    detailQuery,
    modalState,
    setModalState,
    successMessage,
    approveMutation,
    rejectMutation,
    selectedIds,
    toggleSelect,
    toggleSelectAll,
    bulkApproveMutation,
  } = useKycQueue();

  const flaggedQuery = useFlaggedKycAccounts(tab === 'flagged');

  const handleApprove = () => {
    if (modalState.kind === 'closed') return;
    approveMutation.mutate(modalState.submissionId);
  };

  const handleReject = (reason: string) => {
    if (modalState.kind === 'closed') return;
    rejectMutation.mutate({ id: modalState.submissionId, reason });
  };

  return (
    <AdminShell title="KYC Review Queue">
      <div className="mb-6 flex gap-1 border-b border-border">
        <button
          type="button"
          onClick={() => setTab('queue')}
          className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
            tab === 'queue'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
        >
          Review Queue
        </button>
        <button
          type="button"
          onClick={() => setTab('flagged')}
          className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${
            tab === 'flagged'
              ? 'border-primary text-primary'
              : 'border-transparent text-muted-foreground hover:text-foreground'
          }`}
          data-testid="flagged-accounts-tab"
        >
          Flagged Accounts
        </button>
      </div>

      {successMessage ? (
        <div className="mb-6 rounded-lg border border-success/30 bg-success/10 px-4 py-3 text-sm text-success">
          {successMessage}
        </div>
      ) : null}

      {tab === 'queue' ? (
        <>
          {queueQuery.isLoading ? (
            <div className="py-12 text-center text-muted-foreground">Loading queue…</div>
          ) : null}

          {queueQuery.isError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              Failed to load queue. Check that the KYC Service is running and your admin token is
              correct.
              <button type="button" onClick={() => queueQuery.refetch()} className="ml-2 underline">
                Retry
              </button>
            </div>
          ) : null}

          {queueQuery.data && selectedIds.size > 0 ? (
            <div className="mb-4 flex items-center justify-between rounded-lg bg-card-secondary px-4 py-2">
              <span className="text-sm text-muted-foreground">{selectedIds.size} selected</span>
              <Button
                size="sm"
                disabled={bulkApproveMutation.isPending}
                onClick={() => bulkApproveMutation.mutate(Array.from(selectedIds))}
              >
                {bulkApproveMutation.isPending
                  ? 'Approving…'
                  : `Approve Selected (${selectedIds.size})`}
              </Button>
            </div>
          ) : null}

          {queueQuery.data ? (
            <QueueTable
              items={queueQuery.data.items}
              onReview={(id) => setModalState({ kind: 'detail', submissionId: id })}
              selectedIds={selectedIds}
              onToggleSelect={toggleSelect}
              onToggleSelectAll={toggleSelectAll}
            />
          ) : null}

          {queueQuery.data ? (
            <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
              <span>{queueQuery.data.items.length} submission(s) awaiting review</span>
              <button type="button" onClick={() => queueQuery.refetch()} className="hover:text-foreground">
                Refresh
              </button>
            </div>
          ) : null}
        </>
      ) : (
        <>
          {flaggedQuery.isLoading ? (
            <div className="py-12 text-center text-muted-foreground">Loading flagged accounts…</div>
          ) : null}

          {flaggedQuery.isError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
              Failed to load flagged accounts.
              <button
                type="button"
                onClick={() => flaggedQuery.refetch()}
                className="ml-2 underline"
              >
                Retry
              </button>
            </div>
          ) : null}

          {flaggedQuery.data ? (
            <UsersTable
              items={flaggedQuery.data.items}
              onSelect={(id) => navigate(`/users/${id}`)}
            />
          ) : null}
        </>
      )}

      {modalState.kind !== 'closed' && detailQuery.data ? (
        <ReviewModal
          detail={detailQuery.data}
          isApproving={approveMutation.isPending}
          isRejecting={rejectMutation.isPending}
          onApprove={handleApprove}
          onReject={handleReject}
          onClose={() => setModalState({ kind: 'closed' })}
        />
      ) : null}

      {modalState.kind !== 'closed' && detailQuery.isLoading ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70">
          <div className="rounded-xl bg-card px-8 py-6 text-muted-foreground shadow-lg">
            Loading submission details…
          </div>
        </div>
      ) : null}
    </AdminShell>
  );
}
