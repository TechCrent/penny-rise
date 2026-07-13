import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { AdminShell } from '../../components/layout/AdminShell';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
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
        <div className="py-12 text-center text-muted-foreground">Loading…</div>
      </AdminShell>
    );
  }

  if (detailQuery.isError || !detailQuery.data) {
    return (
      <AdminShell title="Dispute">
        <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
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
      <div className="mb-6 flex items-start justify-between">
        <p className="font-mono text-sm text-muted-foreground">
          Raised by {dispute.raisedByUserId.slice(0, 8)}…
        </p>
        <div className="flex gap-2">
          <PriorityBadge priority={dispute.priority} />
          <Badge variant="neutral">{dispute.status}</Badge>
        </div>
      </div>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Description</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="whitespace-pre-wrap text-foreground">{dispute.description}</p>
        </CardContent>
      </Card>

      <Card className="mb-4">
        <CardHeader>
          <CardTitle>Related Entity</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">Type: {dispute.relatedEntityType}</p>
          <p className="text-muted-foreground">ID: {dispute.relatedEntityId}</p>
          {link ? (
            <Link to={link} className="text-sm text-primary underline" data-testid="related-entity-link">
              View {dispute.relatedEntityType.toLowerCase()}
            </Link>
          ) : (
            <p className="mt-1 text-sm text-disabled-foreground">
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
            <p className="text-disabled-foreground">Not yet assigned.</p>
          )}
          {dispute.assignmentHistory.map((entry, i) => (
            <div key={i} className="flex justify-between py-1 text-sm text-foreground">
              <span>{entry.adminName}</span>
              <span className="text-disabled-foreground">
                {new Date(entry.assignedAt).toLocaleString()}
              </span>
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
                className="mb-2 w-full resize-none rounded-md border border-input bg-card-secondary p-2 text-sm text-foreground focus:ring-2 focus:ring-ring focus:outline-none"
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
                className="mb-2 w-full resize-none rounded-md border border-input bg-card-secondary p-2 text-sm text-foreground focus:ring-2 focus:ring-ring focus:outline-none"
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
        <p className="text-sm text-disabled-foreground">
          This dispute must be assigned before it can be resolved or closed — go back to the queue
          and assign it first.
        </p>
      )}
    </AdminShell>
  );
}
