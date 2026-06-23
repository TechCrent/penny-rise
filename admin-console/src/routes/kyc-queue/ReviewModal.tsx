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
      className="fixed inset-0 bg-black/50 z-50 flex items-start justify-center pt-8 px-4 pb-8 overflow-y-auto"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="presentation"
    >
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-4xl">
        <div className="flex items-center justify-between px-8 py-5 border-b border-slate-200">
          <h2 className="text-xl font-bold text-slate-900">KYC Review</h2>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 text-2xl leading-none"
            aria-label="Close"
          >
            ×
          </button>
        </div>

        <div className="px-8 py-6 grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div>
            <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-4">
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
                  <dt className="text-xs font-medium text-slate-500 mb-1">Provider Decision</dt>
                  {detail.provider_decisions.map((d, i) => (
                    <dd key={i} className="text-sm text-slate-700">
                      <span
                        className={`font-semibold ${d.decision === 'PASS' ? 'text-green-600' : 'text-red-600'}`}
                      >
                        {d.decision}
                      </span>
                      {d.confidence_score !== null ? (
                        <span className="text-slate-400 ml-2">
                          ({Math.round(d.confidence_score * 100)}% confidence)
                        </span>
                      ) : null}
                    </dd>
                  ))}
                </div>
              ) : null}
            </dl>

            {showRejectForm ? (
              <div className="mt-6 p-4 bg-red-50 rounded-lg border border-red-200">
                <label
                  htmlFor="reject-reason"
                  className="block text-sm font-medium text-red-800 mb-2"
                >
                  Rejection Reason <span className="text-red-600">*</span>
                </label>
                <textarea
                  id="reject-reason"
                  className="w-full border border-red-300 rounded-md p-2 text-sm text-slate-800 resize-none focus:outline-none focus:ring-2 focus:ring-red-400"
                  rows={3}
                  placeholder="Explain why this submission is being rejected…"
                  value={rejectReason}
                  onChange={(e) => {
                    setRejectReason(e.target.value);
                    setRejectError('');
                  }}
                />
                {rejectError ? <p className="text-xs text-red-600 mt-1">{rejectError}</p> : null}
                <div className="flex gap-2 mt-3">
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
            <h3 className="text-sm font-semibold text-slate-500 uppercase tracking-wide mb-4">
              Documents
            </h3>
            <div className="space-y-4">
              {Object.entries(detail.document_view_urls).map(([type, url]) => (
                <div key={type}>
                  <p className="text-xs font-medium text-slate-500 mb-1">
                    {DOC_LABELS[type] ?? type}
                  </p>
                  <img
                    src={url}
                    alt={DOC_LABELS[type] ?? type}
                    className="w-full rounded-lg border border-slate-200 object-cover max-h-64"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = 'none';
                    }}
                  />
                </div>
              ))}
              {Object.keys(detail.document_view_urls).length === 0 ? (
                <p className="text-sm text-slate-400 italic">No documents available for preview.</p>
              ) : null}
            </div>
          </div>
        </div>

        {!showRejectForm ? (
          <div className="px-8 py-5 border-t border-slate-200 flex items-center gap-3 justify-end">
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() => setShowRejectForm(true)}
              disabled={isApproving}
            >
              Reject
            </Button>
            <Button
              onClick={onApprove}
              disabled={isApproving}
              className="bg-green-600 hover:bg-green-700 text-white"
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
      <dt className="text-xs font-medium text-slate-500">{label}</dt>
      <dd className={`text-sm text-slate-800 mt-0.5 ${valueClassName}`}>{value}</dd>
    </div>
  );
}
