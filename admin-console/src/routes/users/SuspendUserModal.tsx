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
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
      role="presentation"
    >
      <div className="w-full max-w-md rounded-xl border border-border bg-card shadow-lg">
        <div className="border-b border-border px-6 py-5">
          <h2 className="text-lg font-bold text-foreground">Suspend this user?</h2>
        </div>

        <div className="px-6 py-5">
          <p className="mb-3 text-sm text-muted-foreground">
            The user will be immediately unable to log in or use any authenticated endpoint. Funds
            in their vaults and susu groups remain safe and untouched until you restore the account.
          </p>

          <label htmlFor="suspend-reason" className="sr-only">
            Suspension reason
          </label>
          <textarea
            id="suspend-reason"
            aria-label="Suspension reason"
            className="w-full resize-none rounded-md border border-input bg-card-secondary p-2 text-sm text-foreground focus:ring-2 focus:ring-ring focus:outline-none"
            rows={3}
            placeholder="Reason for suspension (required)"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </div>

        <div className="flex items-center justify-end gap-3 border-t border-border px-6 py-4">
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
