import { Badge } from '@/components/ui/badge';
import type { DisputePriority } from '../../api/disputesAdmin';

// AC's URGENT/red, HIGH/amber, NORMAL/blue, LOW/grey mapped onto the real
// priority values — URGENT doesn't exist in the schema, CRITICAL does.
const PRIORITY_VARIANTS: Record<DisputePriority, 'destructive' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'destructive',
  HIGH: 'warning',
  NORMAL: 'info',
  LOW: 'neutral',
};

export function PriorityBadge({ priority }: { priority: DisputePriority }) {
  return (
    <Badge variant={PRIORITY_VARIANTS[priority]} data-testid={`priority-badge-${priority}`}>
      {priority}
    </Badge>
  );
}
