import { Button } from '@/components/ui/button';

interface Props {
  title: string;
  description: string;
  confirmLabel: string;
  isSubmitting: boolean;
  variant?: 'default' | 'destructive';
  onConfirm: () => void;
  onCancel: () => void;
}

/** Shared by Restore and Force Logout — neither needs a reason input, unlike Suspend. */
export function ConfirmActionModal({
  title,
  description,
  confirmLabel,
  isSubmitting,
  variant = 'default',
  onConfirm,
  onCancel,
}: Props) {
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
          <h2 className="text-lg font-bold text-foreground">{title}</h2>
        </div>

        <div className="px-6 py-5">
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>

        <div className="flex items-center justify-end gap-3 border-t border-border px-6 py-4">
          <Button variant="outline" onClick={onCancel} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button variant={variant} disabled={isSubmitting} onClick={onConfirm}>
            {isSubmitting ? 'Working…' : confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
