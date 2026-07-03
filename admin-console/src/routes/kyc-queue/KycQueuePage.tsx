import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AdminShell } from '../../components/layout/AdminShell';
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
      <div className="mb-6 px-4 py-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
        <strong>Placeholder authentication active.</strong> This console uses a shared token for
        local dev and staging only. Real admin auth ships in v0.5.
      </div>

      <div className="mb-6 flex gap-1 border-b border-slate-200">
        <button
          type="button"
          onClick={() => setTab('queue')}
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
            tab === 'queue'
              ? 'border-slate-900 text-slate-900'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
        >
          Review Queue
        </button>
        <button
          type="button"
          onClick={() => setTab('flagged')}
          className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px ${
            tab === 'flagged'
              ? 'border-slate-900 text-slate-900'
              : 'border-transparent text-slate-500 hover:text-slate-700'
          }`}
          data-testid="flagged-accounts-tab"
        >
          Flagged Accounts
        </button>
      </div>

      {successMessage ? (
        <div className="mb-6 px-4 py-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800">
          {successMessage}
        </div>
      ) : null}

      {tab === 'queue' ? (
        <>
          {queueQuery.isLoading ? (
            <div className="text-center py-12 text-slate-400">Loading queue…</div>
          ) : null}

          {queueQuery.isError ? (
            <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700">
              Failed to load queue. Check that the KYC Service is running and your admin token is
              correct.
              <button type="button" onClick={() => queueQuery.refetch()} className="ml-2 underline">
                Retry
              </button>
            </div>
          ) : null}

          {queueQuery.data ? (
            <QueueTable
              items={queueQuery.data.items}
              onReview={(id) => setModalState({ kind: 'detail', submissionId: id })}
            />
          ) : null}

          {queueQuery.data ? (
            <div className="mt-4 flex items-center justify-between text-sm text-slate-400">
              <span>{queueQuery.data.items.length} submission(s) awaiting review</span>
              <button
                type="button"
                onClick={() => queueQuery.refetch()}
                className="hover:text-slate-600"
              >
                Refresh
              </button>
            </div>
          ) : null}
        </>
      ) : (
        <>
          {flaggedQuery.isLoading ? (
            <div className="text-center py-12 text-slate-400">Loading flagged accounts…</div>
          ) : null}

          {flaggedQuery.isError ? (
            <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700">
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
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center">
          <div className="bg-white rounded-xl px-8 py-6 text-slate-600">
            Loading submission details…
          </div>
        </div>
      ) : null}
    </AdminShell>
  );
}
