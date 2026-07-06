import { useState } from 'react';
import { Link } from 'react-router-dom';
import { format } from 'date-fns';
import { AdminShell } from '../../components/layout/AdminShell';
import { Button } from '@/components/ui/button';
import {
  useFlaggedSusuGroups,
  useAllSusuGroups,
  useClearSusuGroupFlag,
} from './useFlaggedSusuGroups';

type Tab = 'flagged' | 'all';

function formatCedis(pesewas: number): string {
  return (pesewas / 100).toFixed(2);
}

export default function FlaggedSusuGroupsPage() {
  const [tab, setTab] = useState<Tab>('flagged');
  const [page, setPage] = useState(0);

  const flaggedQuery = useFlaggedSusuGroups(page, tab === 'flagged');
  const allQuery = useAllSusuGroups(page, tab === 'all');
  const clearFlagMutation = useClearSusuGroupFlag();

  const { data, isLoading, isError } = tab === 'flagged' ? flaggedQuery : allQuery;
  const items = data?.items ?? [];

  const switchTab = (nextTab: Tab) => {
    setTab(nextTab);
    setPage(0);
  };

  return (
    <AdminShell title="Susu Groups">
      <div className="flex gap-2 mb-4 border-b border-slate-200">
        <button
          type="button"
          onClick={() => switchTab('flagged')}
          className={`px-4 py-2 text-sm font-medium border-b-2 ${
            tab === 'flagged'
              ? 'border-slate-900 text-slate-900'
              : 'border-transparent text-slate-500'
          }`}
          data-testid="flagged-tab"
        >
          Flagged for Review
        </button>
        <button
          type="button"
          onClick={() => switchTab('all')}
          className={`px-4 py-2 text-sm font-medium border-b-2 ${
            tab === 'all' ? 'border-slate-900 text-slate-900' : 'border-transparent text-slate-500'
          }`}
          data-testid="all-groups-tab"
        >
          All Groups
        </button>
      </div>

      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700 mb-4">
          Couldn&apos;t load susu groups.
        </div>
      )}

      {isLoading ? (
        <div className="text-center py-12 text-slate-400">Loading…</div>
      ) : items.length === 0 ? (
        <div
          className="bg-white rounded-xl border border-slate-200 p-12 text-center"
          data-testid="susu-groups-empty-state"
        >
          <p className="text-slate-400 text-lg">
            {tab === 'flagged'
              ? 'No susu groups are currently flagged for review.'
              : 'No susu groups found.'}
          </p>
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
                  Status
                </th>
                <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                  Flagged At
                </th>
                {tab === 'flagged' && (
                  <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
                    Last Shortfall
                  </th>
                )}
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
                  data-testid={`susu-group-row-${group.id}`}
                >
                  <td className="px-6 py-4 font-medium text-slate-900">{group.name}</td>
                  <td className="px-6 py-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700">
                      {group.status}
                    </span>
                    {group.flaggedForReview && (
                      <span className="ml-2 inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700">
                        Flagged
                      </span>
                    )}
                  </td>
                  <td className="px-6 py-4 text-slate-600">
                    {group.flaggedAt
                      ? format(new Date(group.flaggedAt), 'dd MMM yyyy, HH:mm')
                      : '—'}
                  </td>
                  {tab === 'flagged' && (
                    <td className="px-6 py-4 text-slate-600">
                      {group.lastShortfallRoundNumber != null ? (
                        <>Round {group.lastShortfallRoundNumber} — penalty waived</>
                      ) : (
                        '—'
                      )}
                    </td>
                  )}
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
                    {group.flaggedForReview && (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={clearFlagMutation.isPending}
                        onClick={() => clearFlagMutation.mutate(group.id)}
                      >
                        Clear Flag
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex justify-between items-center mt-4 text-sm text-slate-500">
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </AdminShell>
  );
}
