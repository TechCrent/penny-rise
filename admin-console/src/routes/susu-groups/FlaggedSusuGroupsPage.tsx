import { Link } from 'react-router-dom';
import { format } from 'date-fns';
import { AdminShell } from '../../components/layout/AdminShell';
import { Button } from '@/components/ui/button';
import { useFlaggedSusuGroups, useClearSusuGroupFlag } from './useFlaggedSusuGroups';

function formatCedis(pesewas: number): string {
  return (pesewas / 100).toFixed(2);
}

export default function FlaggedSusuGroupsPage() {
  const { data, isLoading, isError } = useFlaggedSusuGroups(0);
  const clearFlagMutation = useClearSusuGroupFlag();

  const items = data?.items ?? [];

  return (
    <AdminShell title="Flagged Susu Groups">
      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700 mb-4">
          Couldn&apos;t load flagged susu groups.
        </div>
      )}

      {isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading…</div>
      ) : items.length === 0 ? (
        <div
          className="bg-white rounded-xl border border-slate-200 p-12 text-center"
          data-testid="flagged-susu-empty-state"
        >
          <p className="text-slate-400 text-lg">No susu groups are currently flagged for review.</p>
        </div>
      ) : (
        <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  Group
                </th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  Flagged At
                </th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  Last Shortfall
                </th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  Pot Balance
                </th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  Organiser
                </th>
                <th className="px-6 py-3" />
              </tr>
            </thead>
            <tbody>
              {items.map((group, index) => (
                <tr
                  key={group.id}
                  className={`border-b border-slate-100 ${index === items.length - 1 ? 'border-0' : ''}`}
                  data-testid={`flagged-susu-row-${group.id}`}
                >
                  <td className="px-6 py-4 font-medium text-slate-900">{group.name}</td>
                  <td className="px-6 py-4 text-slate-600">
                    {format(new Date(group.flaggedAt), 'dd MMM yyyy, HH:mm')}
                  </td>
                  <td className="px-6 py-4 text-slate-600">
                    {group.lastShortfallRoundNumber != null ? (
                      <>Round {group.lastShortfallRoundNumber} — penalty waived</>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="px-6 py-4 text-slate-600">
                    GHS {formatCedis(group.potBalancePesewas)}
                  </td>
                  <td className="px-6 py-4">
                    <Link
                      to={`/users/${group.organiserUserId}`}
                      className="text-blue-600 underline"
                      data-testid={`organiser-link-${group.id}`}
                    >
                      View organiser
                    </Link>
                  </td>
                  <td className="px-6 py-4">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={clearFlagMutation.isPending}
                      onClick={() => clearFlagMutation.mutate(group.id)}
                    >
                      Clear Flag
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </AdminShell>
  );
}
