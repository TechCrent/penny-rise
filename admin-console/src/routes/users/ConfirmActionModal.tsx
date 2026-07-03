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
      className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center px-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel();
      }}
      role="presentation"
    >
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md">
        <div className="px-6 py-5 border-b border-slate-200">
          <h2 className="text-lg font-bold text-slate-900">{title}</h2>
        </div>

        <div className="px-6 py-5">
          <p className="text-sm text-slate-500">{description}</p>
        </div>

        <div className="px-6 py-4 border-t border-slate-200 flex items-center gap-3 justify-end">
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
