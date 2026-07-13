import { formatDistanceToNow, differenceInHours } from 'date-fns';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { PriorityBadge } from './PriorityBadge';
import type { AdminDisputeListItem } from '../../api/disputesAdmin';

interface DisputesTableProps {
  items: AdminDisputeListItem[];
  onSelect: (id: string) => void;
  onAssign: (id: string) => void;
  assigningId: string | undefined;
  isAssigning: boolean;
  selectedIds: Set<string>;
  onToggleSelect: (id: string) => void;
  onToggleSelectAllResolvable: () => void;
}

function TimeInQueue({ createdAt }: { createdAt: string }) {
  const created = new Date(createdAt);
  const distance = formatDistanceToNow(created);
  const hours = differenceInHours(new Date(), created);

  const color =
    hours >= 24 ? 'font-semibold text-destructive' : hours >= 4 ? 'text-warning' : 'text-muted-foreground';

  return <span className={color}>{distance}</span>;
}

export function DisputesTable({
  items,
  onSelect,
  onAssign,
  assigningId,
  isAssigning,
  selectedIds,
  onToggleSelect,
  onToggleSelectAllResolvable,
}: DisputesTableProps) {
  if (items.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-12 text-center">
        <p className="text-lg text-muted-foreground">No disputes in this view.</p>
      </div>
    );
  }

  const resolvable = items.filter((d) => d.status === 'IN_REVIEW');
  const allResolvableSelected =
    resolvable.length > 0 && resolvable.every((d) => selectedIds.has(d.id));

  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-table-header">
          <TableHead className="w-10">
            <input
              type="checkbox"
              aria-label="Select all resolvable disputes"
              checked={allResolvableSelected}
              disabled={resolvable.length === 0}
              onChange={onToggleSelectAllResolvable}
            />
          </TableHead>
          <TableHead>Priority</TableHead>
          <TableHead>Subject</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Raised By</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Time in Queue</TableHead>
          <TableHead />
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((d) => (
          <TableRow key={d.id} data-testid={`dispute-row-${d.id}`}>
            <TableCell>
              {d.status === 'IN_REVIEW' && (
                <input
                  type="checkbox"
                  aria-label={`Select dispute ${d.id}`}
                  checked={selectedIds.has(d.id)}
                  onChange={() => onToggleSelect(d.id)}
                />
              )}
            </TableCell>
            <TableCell>
              <PriorityBadge priority={d.priority} />
            </TableCell>
            <TableCell
              className="cursor-pointer font-medium text-foreground hover:underline"
              onClick={() => onSelect(d.id)}
            >
              {d.subject}
            </TableCell>
            <TableCell className="text-muted-foreground">{d.disputeType}</TableCell>
            <TableCell className="font-mono text-xs text-disabled-foreground">
              {d.raisedByUserId.slice(0, 8)}…
            </TableCell>
            <TableCell>
              <Badge variant="neutral">{d.status}</Badge>
            </TableCell>
            <TableCell>
              <TimeInQueue createdAt={d.createdAt} />
            </TableCell>
            <TableCell className="text-right">
              {d.status === 'OPEN' && (
                <Button
                  size="sm"
                  disabled={isAssigning && assigningId === d.id}
                  onClick={() => onAssign(d.id)}
                >
                  {isAssigning && assigningId === d.id ? 'Assigning…' : 'Assign to Me'}
                </Button>
              )}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
