import { formatDistanceToNow, format, differenceInHours } from 'date-fns';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import type { QueueItem } from '../../api/kycAdmin';

interface QueueTableProps {
  items: QueueItem[];
  onReview: (submissionId: string) => void;
  selectedIds: Set<string>;
  onToggleSelect: (submissionId: string) => void;
  onToggleSelectAll: () => void;
}

export function QueueTable({
  items,
  onReview,
  selectedIds,
  onToggleSelect,
  onToggleSelectAll,
}: QueueTableProps) {
  if (items.length === 0) {
    return (
      <div className="rounded-xl border border-border bg-card p-12 text-center">
        <p className="text-lg text-muted-foreground">No submissions awaiting review.</p>
        <p className="mt-1 text-sm text-disabled-foreground">
          The queue is clear — check back later.
        </p>
      </div>
    );
  }

  const allSelected = items.length > 0 && items.every((i) => selectedIds.has(i.submission_id));

  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-table-header">
          <TableHead className="w-10">
            <input
              type="checkbox"
              aria-label="Select all submissions"
              checked={allSelected}
              onChange={onToggleSelectAll}
            />
          </TableHead>
          <TableHead>Applicant</TableHead>
          <TableHead>Submitted</TableHead>
          <TableHead>Time in Queue</TableHead>
          <TableHead>Flag Reason</TableHead>
          <TableHead />
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => (
          <TableRow key={item.submission_id}>
            <TableCell>
              <input
                type="checkbox"
                aria-label={`Select submission ${item.submission_id}`}
                checked={selectedIds.has(item.submission_id)}
                onChange={() => onToggleSelect(item.submission_id)}
              />
            </TableCell>
            <TableCell>
              <div className="font-medium text-foreground">{item.full_name_on_card}</div>
              <div className="mt-0.5 font-mono text-xs text-disabled-foreground">
                {item.user_id.slice(0, 8)}…
              </div>
            </TableCell>
            <TableCell className="text-muted-foreground">
              {format(new Date(item.submitted_at), 'dd MMM yyyy, HH:mm')}
            </TableCell>
            <TableCell>
              <TimeInQueue submittedAt={item.submitted_at} />
            </TableCell>
            <TableCell>
              {item.flag_reason ? (
                <Badge variant="warning">{item.flag_reason}</Badge>
              ) : (
                <span className="text-disabled-foreground">—</span>
              )}
            </TableCell>
            <TableCell className="text-right">
              <Button size="sm" onClick={() => onReview(item.submission_id)}>
                Review
              </Button>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function TimeInQueue({ submittedAt }: { submittedAt: string }) {
  const submitted = new Date(submittedAt);
  const distance = formatDistanceToNow(submitted);
  const hours = differenceInHours(submitted, new Date());

  const color =
    hours <= -24
      ? 'font-semibold text-destructive'
      : hours <= -4
        ? 'text-warning'
        : 'text-muted-foreground';

  return <span className={color}>{distance}</span>;
}
