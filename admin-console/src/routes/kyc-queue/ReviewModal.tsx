import { useState } from 'react';
import { format, formatDistanceToNow } from 'date-fns';
import { type SubmissionDetail, maskGhanaCardNumber } from '../../api/kycAdmin';
import { Button } from '@/components/ui/button';

interface ReviewModalProps {
  detail: SubmissionDetail;
  isApproving: boolean;
  isRejecting: boolean;
  onApprove: () => void;
  onReject: (reason: string) => void;
  onClose: () => void;
}

const DOC_LABELS: Record<string, string> = {
  FRONT_OF_CARD: 'Front of Card',
  BACK_OF_CARD: 'Back of Card',
  SELFIE: 'Selfie with Card',
};

export function ReviewModal({
  detail,
  isApproving,
  isRejecting,
  onApprove,
  onReject,
  onClose,
}: ReviewModalProps) {
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [rejectError, setRejectError] = useState('');

  const handleRejectSubmit = () => {
    if (!rejectReason.trim()) {
      setRejectError('A reason is required to reject a submission.');
      return;
    }
    onReject(rejectReason.trim());
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-black/70 px-4 pt-8 pb-8"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="presentation"
    >
      <div className="w-full max-w-4xl rounded-xl border border-border bg-card shadow-lg">
        <div className="flex items-center justify-between border-b border-border px-8 py-5">
          <h2 className="text-xl font-bold text-foreground">KYC Review</h2>
          <button
            type="button"
            onClick={onClose}
            className="text-2xl leading-none text-muted-foreground hover:text-foreground"
            aria-label="Close"
          >
            ×
          </button>
        </div>

        <div className="grid grid-cols-1 gap-8 px-8 py-6 lg:grid-cols-2">
          <div>
            <h3 className="mb-4 text-sm font-semibold tracking-wide text-muted-foreground uppercase">
              Submission Details
            </h3>

            <dl className="space-y-3">
              <DetailRow label="Full Name (on card)" value={detail.full_name_on_card} />
              <DetailRow
                label="Ghana Card Number"
                value={maskGhanaCardNumber(detail.ghana_card_number)}
                valueClassName="font-mono"
              />
              {detail.date_of_birth ? (
                <DetailRow
                  label="Date of Birth"
                  value={format(new Date(detail.date_of_birth), 'dd MMMM yyyy')}
                />
              ) : null}
              {detail.phone_number ? (
                <DetailRow label="Phone Number" value={detail.phone_number} />
              ) : null}
              <DetailRow
                label="Submitted"
                value={formatDistanceToNow(new Date(detail.submitted_at), { addSuffix: true })}
              />
              <DetailRow label="Review Path" value={detail.review_path ?? '—'} />

              {detail.provider_decisions.length > 0 ? (
                <div>
                  <dt className="mb-1 text-xs font-medium text-muted-foreground">
                    Provider Decision
                  </dt>
                  {detail.provider_decisions.map((d, i) => (
                    <dd key={i} className="text-sm text-foreground">
                      <span
                        className={`font-semibold ${d.decision === 'PASS' ? 'text-success' : 'text-destructive'}`}
                      >
                        {d.decision}
                      </span>
                      {d.confidence_score !== null ? (
                        <span className="ml-2 text-disabled-foreground">
                          ({Math.round(d.confidence_score * 100)}% confidence)
                        </span>
                      ) : null}
                    </dd>
                  ))}
                </div>
              ) : null}
            </dl>

            {showRejectForm ? (
              <div className="mt-6 rounded-lg border border-destructive/30 bg-destructive/10 p-4">
                <label htmlFor="reject-reason" className="mb-2 block text-sm font-medium text-destructive">
                  Rejection Reason <span className="text-destructive">*</span>
                </label>
                <textarea
                  id="reject-reason"
                  className="w-full resize-none rounded-md border border-destructive/40 bg-card p-2 text-sm text-foreground focus:ring-2 focus:ring-destructive/40 focus:outline-none"
                  rows={3}
                  placeholder="Explain why this submission is being rejected…"
                  value={rejectReason}
                  onChange={(e) => {
                    setRejectReason(e.target.value);
                    setRejectError('');
                  }}
                />
                {rejectError ? <p className="mt-1 text-xs text-destructive">{rejectError}</p> : null}
                <div className="mt-3 flex gap-2">
                  <Button
                    variant="destructive"
                    size="sm"
                    onClick={handleRejectSubmit}
                    disabled={isRejecting}
                  >
                    {isRejecting ? 'Rejecting…' : 'Confirm Rejection'}
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      setShowRejectForm(false);
                      setRejectReason('');
                      setRejectError('');
                    }}
                  >
                    Cancel
                  </Button>
                </div>
              </div>
            ) : null}
          </div>

          <div>
            <h3 className="mb-4 text-sm font-semibold tracking-wide text-muted-foreground uppercase">
              Documents
            </h3>
            <div className="space-y-4">
              {Object.entries(detail.document_view_urls).map(([type, url]) => (
                <div key={type}>
                  <p className="mb-1 text-xs font-medium text-muted-foreground">
                    {DOC_LABELS[type] ?? type}
                  </p>
                  <a
                    href={url}
                    target="_blank"
                    rel="noopener noreferrer"
                    title="Click to view full size"
                    className="block cursor-zoom-in"
                  >
                    <img
                      src={url}
                      alt={DOC_LABELS[type] ?? type}
                      className="max-h-64 w-full rounded-lg border border-border object-cover transition-opacity hover:opacity-90"
                      onError={(e) => {
                        const img = e.target as HTMLImageElement;
                        img.style.display = 'none';
                        img.dataset.failed = 'true';
                      }}
                    />
                  </a>
                </div>
              ))}
              {Object.keys(detail.document_view_urls).length === 0 ? (
                <p className="text-sm text-disabled-foreground italic">
                  No documents available for preview.
                </p>
              ) : null}
            </div>
          </div>
        </div>

        {!showRejectForm ? (
          <div className="flex items-center justify-end gap-3 border-t border-border px-8 py-5">
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={() => setShowRejectForm(true)} disabled={isApproving}>
              Reject
            </Button>
            <Button
              onClick={onApprove}
              disabled={isApproving}
              className="bg-success text-success-foreground hover:bg-success/85"
            >
              {isApproving ? 'Approving…' : 'Approve'}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  );
}

function DetailRow({
  label,
  value,
  valueClassName = '',
}: {
  label: string;
  value: string;
  valueClassName?: string;
}) {
  return (
    <div>
      <dt className="text-xs font-medium text-muted-foreground">{label}</dt>
      <dd className={`mt-0.5 text-sm text-foreground ${valueClassName}`}>{value}</dd>
    </div>
  );
}
