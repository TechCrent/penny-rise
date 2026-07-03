import type { DisputePriority } from '../../api/disputesAdmin';

// AC's URGENT/red, HIGH/amber, NORMAL/blue, LOW/grey mapped onto the real
// priority values — URGENT doesn't exist in the schema, CRITICAL does.
const PRIORITY_STYLES: Record<DisputePriority, string> = {
  CRITICAL: 'bg-red-100 text-red-700',
  HIGH: 'bg-amber-100 text-amber-700',
  NORMAL: 'bg-blue-100 text-blue-700',
  LOW: 'bg-slate-100 text-slate-600',
};

export function PriorityBadge({ priority }: { priority: DisputePriority }) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${PRIORITY_STYLES[priority]}`}
      data-testid={`priority-badge-${priority}`}
    >
      {priority}
    </span>
  );
}
