import { useState } from 'react';
import { AdminShell } from '../../components/layout/AdminShell';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { AuditLogTable } from './AuditLogTable';
import { useAuditLog, useAuditLogFacets } from './useAuditLog';
import type { AuditLogFilterParams } from '../../api/auditLogAdmin';

type Filters = Omit<AuditLogFilterParams, 'cursor' | 'limit'>;

export default function AuditLogPage() {
  const [filters, setFilters] = useState<Filters>({});
  const { data: facets } = useAuditLogFacets();
  const { entries, isLoading, isError, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useAuditLog(filters);

  return (
    <AdminShell title="Audit Log">
      <div className="mb-4 flex flex-wrap gap-3">
        <Input
          placeholder="Actor ID (UUID)"
          aria-label="Filter by actor ID"
          value={filters.actorId ?? ''}
          onChange={(e) => setFilters({ ...filters, actorId: e.target.value || undefined })}
          className="max-w-xs"
        />

        <select
          aria-label="Filter by event type"
          className="h-8 rounded-lg border border-input bg-card-secondary px-2.5 text-sm text-foreground"
          value={filters.eventType ?? ''}
          onChange={(e) => setFilters({ ...filters, eventType: e.target.value || undefined })}
        >
          <option value="">All event types</option>
          {facets?.eventTypes.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>

        <select
          aria-label="Filter by target entity type"
          className="h-8 rounded-lg border border-input bg-card-secondary px-2.5 text-sm text-foreground"
          value={filters.targetEntityType ?? ''}
          onChange={(e) =>
            setFilters({ ...filters, targetEntityType: e.target.value || undefined })
          }
        >
          <option value="">All entity types</option>
          {facets?.targetEntityTypes.map((t) => (
            <option key={t} value={t}>
              {t}
            </option>
          ))}
        </select>

        <Input
          type="date"
          aria-label="From date"
          value={filters.fromDate?.slice(0, 10) ?? ''}
          onChange={(e) =>
            setFilters({
              ...filters,
              fromDate: e.target.value ? `${e.target.value}T00:00:00Z` : undefined,
            })
          }
          className="max-w-[10rem]"
        />
        <Input
          type="date"
          aria-label="To date"
          value={filters.toDate?.slice(0, 10) ?? ''}
          onChange={(e) =>
            setFilters({
              ...filters,
              toDate: e.target.value ? `${e.target.value}T23:59:59Z` : undefined,
            })
          }
          className="max-w-[10rem]"
        />
      </div>

      {isError && (
        <div className="mb-4 rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
          Couldn&apos;t load the audit log.
        </div>
      )}

      {isLoading ? (
        <div className="py-12 text-center text-muted-foreground">Loading…</div>
      ) : (
        <AuditLogTable entries={entries} />
      )}

      {hasNextPage && (
        <div className="mt-4 flex justify-center">
          <Button variant="outline" disabled={isFetchingNextPage} onClick={() => fetchNextPage()}>
            {isFetchingNextPage ? 'Loading…' : 'Load more'}
          </Button>
        </div>
      )}
    </AdminShell>
  );
}
