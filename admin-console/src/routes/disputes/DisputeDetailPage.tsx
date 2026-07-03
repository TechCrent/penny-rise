import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { AdminShell } from '../../components/layout/AdminShell';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PriorityBadge } from './PriorityBadge';
import { relatedEntityLink } from './relatedEntityLink';
import { useDisputeDetail } from './useDisputeDetail';

export default function DisputeDetailPage() {
  const { disputeId } = useParams<{ disputeId: string }>();
  const navigate = useNavigate();
  const { detailQuery, resolve, isResolving, closeNoAction, isClosing } = useDisputeDetail(
    disputeId ?? '',
  );

  const [resolutionText, setResolutionText] = useState('');
  const [closeReasonText, setCloseReasonText] = useState('');

  if (detailQuery.isLoading) {
    return (
      <AdminShell title="Dispute">
        <div className="text-center py-12 text-slate-400">Loading…</div>
      </AdminShell>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <AdminShell title="Dispute">
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700">
          Couldn't load this dispute.
        </div>
      </AdminShell>
    );
  }

  const dispute = detailQuery.data;
  const isActionable = dispute.status === 'IN_REVIEW';
  const link = relatedEntityLink(dispute);

  const handleResolve = () => {
    resolve(resolutionText, { onSuccess: () => navigate('/disputes') });
  };

  const handleClose = () => {
    closeNoAction(closeReasonText, { onSuccess: () => navigate('/disputes') });
  };

  return (
    <AdminShell title={dispute.subject}>
      <div className="flex justify-between items-start mb-6">
        <p className="text-slate-500 text-sm font-mono">
          Raised by {dispute.raisedByUserId.slice(0, 8)}…
        </p>
        <div className="flex gap-2">
          <PriorityBadge priority={dispute.priority} />
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700">
            {dispute.status}
          </span>
        </div>
      </div>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Description</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="whitespace-pre-wrap text-slate-700">{dispute.description}</p>
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Related Entity</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-slate-600">Type: {dispute.relatedEntityType}</p>
          <p className="text-slate-600">ID: {dispute.relatedEntityId}</p>
          {link ? (
            <Link
              to={link}
              className="text-sm underline text-slate-700"
              data-testid="related-entity-link"
            >
              View {dispute.relatedEntityType.toLowerCase()}
            </Link>
          ) : (
            <p className="text-sm text-slate-400 mt-1">
              No admin detail view available for this entity type yet.
            </p>
          )}
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Assignment History</CardTitle>
        </CardHeader>
        <CardContent>
          {dispute.assignmentHistory.length === 0 && (
            <p className="text-slate-400">Not yet assigned.</p>
          )}
          {dispute.assignmentHistory.map((entry, i) => (
            <div key={i} className="flex justify-between py-1 text-sm">
              <span>{entry.adminName}</span>
              <span className="text-slate-400">{new Date(entry.assignedAt).toLocaleString()}</span>
            </div>
          ))}
        </CardContent>
      </Card>

      {isActionable && (
        <div className="grid grid-cols-2 gap-4">
          <Card>
            <CardHeader>
              <CardTitle>Resolve</CardTitle>
            </CardHeader>
            <CardContent>
              <label htmlFor="resolution-notes" className="sr-only">
                Resolution notes
              </label>
              <textarea
                id="resolution-notes"
                aria-label="Resolution notes"
                className="w-full border border-slate-300 rounded-md p-2 text-sm text-slate-800 resize-none focus:outline-none focus:ring-2 focus:ring-slate-400 mb-2"
                rows={3}
                placeholder="Resolution notes (required)"
                value={resolutionText}
                onChange={(e) => setResolutionText(e.target.value)}
              />
              <Button
                disabled={!resolutionText.trim() || isResolving}
                onClick={handleResolve}
                className="w-full"
              >
                {isResolving ? 'Resolving…' : 'Resolve Dispute'}
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Close — No Action</CardTitle>
            </CardHeader>
            <CardContent>
              <label htmlFor="close-reason" className="sr-only">
                Close reason
              </label>
              <textarea
                id="close-reason"
                aria-label="Close reason"
                className="w-full border border-slate-300 rounded-md p-2 text-sm text-slate-800 resize-none focus:outline-none focus:ring-2 focus:ring-slate-400 mb-2"
                rows={3}
                placeholder="Reason for closing without action (required)"
                value={closeReasonText}
                onChange={(e) => setCloseReasonText(e.target.value)}
              />
              <Button
                variant="outline"
                disabled={!closeReasonText.trim() || isClosing}
                onClick={handleClose}
                className="w-full"
              >
                {isClosing ? 'Closing…' : 'Close With No Action'}
              </Button>
            </CardContent>
          </Card>
        </div>
      )}

      {!isActionable && dispute.status === 'OPEN' && (
        <p className="text-sm text-slate-400">
          This dispute must be assigned before it can be resolved or closed — go back to the queue
          and assign it first.
        </p>
      )}
    </AdminShell>
  );
}
