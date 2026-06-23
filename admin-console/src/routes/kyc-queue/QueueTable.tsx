import { formatDistanceToNow, format, differenceInHours } from 'date-fns';
import { Button } from '@/components/ui/button';
import type { QueueItem } from '../../api/kycAdmin';

interface QueueTableProps {
  items: QueueItem[];
  onReview: (submissionId: string) => void;
}

export function QueueTable({ items, onReview }: QueueTableProps) {
  if (items.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-slate-200 p-12 text-center">
        <p className="text-slate-400 text-lg">No submissions awaiting review.</p>
        <p className="text-slate-300 text-sm mt-1">The queue is clear — check back later.</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50">
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Applicant
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Submitted
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Time in Queue
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Flag Reason
            </th>
            <th className="px-6 py-3" />
          </tr>
        </thead>
        <tbody>
          {items.map((item, index) => (
            <tr
              key={item.submission_id}
              className={`border-b border-slate-100 hover:bg-slate-50 transition-colors ${
                index === items.length - 1 ? 'border-0' : ''
              }`}
            >
              <td className="px-6 py-4">
                <div className="font-medium text-slate-900">{item.full_name_on_card}</div>
                <div className="text-slate-400 text-xs mt-0.5 font-mono">
                  {item.user_id.slice(0, 8)}…
                </div>
              </td>
              <td className="px-6 py-4 text-slate-600">
                {format(new Date(item.submitted_at), 'dd MMM yyyy, HH:mm')}
              </td>
              <td className="px-6 py-4">
                <TimeInQueue submittedAt={item.submitted_at} />
              </td>
              <td className="px-6 py-4">
                {item.flag_reason ? (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-amber-100 text-amber-700">
                    {item.flag_reason}
                  </span>
                ) : (
                  <span className="text-slate-300">—</span>
                )}
              </td>
              <td className="px-6 py-4 text-right">
                <Button size="sm" onClick={() => onReview(item.submission_id)}>
                  Review
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TimeInQueue({ submittedAt }: { submittedAt: string }) {
  const submitted = new Date(submittedAt);
  const distance = formatDistanceToNow(submitted);
  const hours = differenceInHours(submitted, new Date());

  const color =
    hours <= -24 ? 'text-red-600 font-semibold' : hours <= -4 ? 'text-amber-600' : 'text-slate-600';

  return <span className={color}>{distance}</span>;
}
