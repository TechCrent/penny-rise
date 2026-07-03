import { useState } from 'react';
import { Button } from '@/components/ui/button';

interface Props {
  isSubmitting: boolean;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export function SuspendUserModal({ isSubmitting, onConfirm, onCancel }: Props) {
  const [reason, setReason] = useState('');

  return (
    <div
      className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center px-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
      role="presentation"
    >
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md">
        <div className="px-6 py-5 border-b border-slate-200">
          <h2 className="text-lg font-bold text-slate-900">Suspend this user?</h2>
        </div>

        <div className="px-6 py-5">
          <p className="text-sm text-slate-500 mb-3">
            The user will be immediately unable to log in or use any authenticated endpoint. Funds
            in their vaults and susu groups remain safe and untouched until you restore the account.
          </p>

          <label htmlFor="suspend-reason" className="sr-only">
            Suspension reason
          </label>
          <textarea
            id="suspend-reason"
            aria-label="Suspension reason"
            className="w-full border border-slate-300 rounded-md p-2 text-sm text-slate-800 resize-none focus:outline-none focus:ring-2 focus:ring-slate-400"
            rows={3}
            placeholder="Reason for suspension (required)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </div>

        <div className="px-6 py-4 border-t border-slate-200 flex items-center gap-3 justify-end">
          <Button variant="outline" onClick={onCancel} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            disabled={!reason.trim() || isSubmitting}
            onClick={() => onConfirm(reason.trim())}
          >
            {isSubmitting ? 'Suspending…' : 'Suspend User'}
          </Button>
        </div>
      </div>
    </div>
  );
}
