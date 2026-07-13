import { useState } from 'react';
import { Link } from 'react-router-dom';
import { format } from 'date-fns';
import { AdminShell } from '../../components/layout/AdminShell';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
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
      <div className="mb-4 flex gap-2 border-b border-border">
        <button
          type="button"
          onClick={() => switchTab('flagged')}
          className={`border-b-2 px-4 py-2 text-sm font-medium ${
            tab === 'flagged' ? 'border-primary text-primary' : 'border-transparent text-muted-foreground'
          }`}
          data-testid="flagged-tab"
        >
          Flagged for Review
        </button>
        <button
          type="button"
          onClick={() => switchTab('all')}
          className={`border-b-2 px-4 py-2 text-sm font-medium ${
            tab === 'all' ? 'border-primary text-primary' : 'border-transparent text-muted-foreground'
          }`}
          data-testid="all-groups-tab"
        >
          All Groups
        </button>
      </div>

      {isError && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Couldn&apos;t load susu groups.
        </div>
      )}

      {isLoading ? (
        <div className="py-12 text-center text-muted-foreground">Loading…</div>
      ) : items.length === 0 ? (
        <div
          className="rounded-xl border border-border bg-card p-12 text-center"
          data-testid="susu-groups-empty-state"
        >
          <p className="text-lg text-muted-foreground">
            {tab === 'flagged'
              ? 'No susu groups are currently flagged for review.'
              : 'No susu groups found.'}
          </p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-table-header">
              <TableHead>Group</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Flagged At</TableHead>
              {tab === 'flagged' && <TableHead>Last Shortfall</TableHead>}
              <TableHead>Pot Balance</TableHead>
              <TableHead>Organiser</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {items.map((group) => (
              <TableRow key={group.id} data-testid={`susu-group-row-${group.id}`}>
                <TableCell className="font-medium text-foreground">{group.name}</TableCell>
                <TableCell>
                  <Badge variant="neutral">{group.status}</Badge>
                  {group.flaggedForReview && (
                    <Badge variant="destructive" className="ml-2">
                      Flagged
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {group.flaggedAt ? format(new Date(group.flaggedAt), 'dd MMM yyyy, HH:mm') : '—'}
                </TableCell>
                {tab === 'flagged' && (
                  <TableCell className="text-muted-foreground">
                    {group.lastShortfallRoundNumber != null ? (
                      <>Round {group.lastShortfallRoundNumber} — penalty waived</>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                )}
                <TableCell className="text-muted-foreground">
                  GHS {formatCedis(group.potBalancePesewas)}
                </TableCell>
                <TableCell>
                  <Link
                    to={`/users/${group.organiserUserId}`}
                    className="text-info underline"
                    data-testid={`organiser-link-${group.id}`}
                  >
                    View organiser
                  </Link>
                </TableCell>
                <TableCell>
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
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {data && data.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
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
