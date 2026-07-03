import { formatDistanceToNow, differenceInHours } from 'date-fns';
import { Button } from '@/components/ui/button';
import { PriorityBadge } from './PriorityBadge';
import type { AdminDisputeListItem } from '../../api/disputesAdmin';

interface DisputesTableProps {
  items: AdminDisputeListItem[];
  onSelect: (id: string) => void;
  onAssign: (id: string) => void;
  assigningId: string | undefined;
  isAssigning: boolean;
}

function TimeInQueue({ createdAt }: { createdAt: string }) {
  const created = new Date(createdAt);
  const distance = formatDistanceToNow(created);
  const hours = differenceInHours(new Date(), created);

  const color =
    hours >= 24 ? 'text-red-600 font-semibold' : hours >= 4 ? 'text-amber-600' : 'text-slate-600';

  return <span className={color}>{distance}</span>;
}

export function DisputesTable({
  items,
  onSelect,
  onAssign,
  assigningId,
  isAssigning,
}: DisputesTableProps) {
  if (items.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
        <p className="text-slate-400 text-lg">No disputes in this view.</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50">
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Priority
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Subject
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Type
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Raised By
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Status
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Time in Queue
            </th>
            <th className="px-6 py-3" />
          </tr>
        </thead>
        <tbody>
          {items.map((d, index) => (
            <tr
              key={d.id}
              className={`border-b border-slate-100 hover:bg-slate-50 transition-colors ${
                index === items.length - 1 ? 'border-0' : ''
              }`}
              data-testid={`dispute-row-${d.id}`}
            >
              <td className="px-6 py-4">
                <PriorityBadge priority={d.priority} />
              </td>
              <td
                className="px-6 py-4 font-medium text-slate-900 cursor-pointer hover:underline"
                onClick={() => onSelect(d.id)}
              >
                {d.subject}
              </td>
              <td className="px-6 py-4 text-slate-600">{d.disputeType}</td>
              <td className="px-6 py-4 text-slate-400 text-xs font-mono">
                {d.raisedByUserId.slice(0, 8)}…
              </td>
              <td className="px-6 py-4">
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700">
                  {d.status}
                </span>
              </td>
              <td className="px-6 py-4">
                <TimeInQueue createdAt={d.createdAt} />
              </td>
              <td className="px-6 py-4 text-right">
                {d.status === 'OPEN' && (
                  <Button
                    size="sm"
                    disabled={isAssigning && assigningId === d.id}
                    onClick={() => onAssign(d.id)}
                  >
                    {isAssigning && assigningId === d.id ? 'Assigning…' : 'Assign to Me'}
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
