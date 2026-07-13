import { Fragment, useState } from 'react';
import { format } from 'date-fns';
import { Link } from 'react-router-dom';
import { Badge } from '@/components/ui/badge';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import type { AuditLogEntry } from '../../api/auditLogAdmin';
import { auditEntityLink } from './auditEntityLink';

interface AuditLogTableProps {
  entries: AuditLogEntry[];
}

export function AuditLogTable({ entries }: AuditLogTableProps) {
  const [expandedId, setExpandedId] = useState<string | null>(null);

  if (entries.length === 0) {
    return (
      <div
        className="rounded-xl border border-border bg-card p-12 text-center"
        data-testid="audit-log-empty-state"
      >
        <p className="text-lg text-muted-foreground">No audit entries match the current filters.</p>
      </div>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow className="hover:bg-table-header">
          <TableHead>Occurred At</TableHead>
          <TableHead>Event</TableHead>
          <TableHead>Actor</TableHead>
          <TableHead>Target</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {entries.map((entry) => {
          const isExpanded = expandedId === entry.eventId;
          const link = auditEntityLink(entry);

          return (
            <Fragment key={entry.eventId}>
              <TableRow
                className="cursor-pointer"
                onClick={() => setExpandedId(isExpanded ? null : entry.eventId)}
                data-testid={`audit-row-${entry.eventId}`}
              >
                <TableCell className="text-muted-foreground">
                  {format(new Date(entry.occurredAt), 'dd MMM yyyy, HH:mm:ss')}
                </TableCell>
                <TableCell>
                  <Badge variant="neutral">{entry.eventType}</Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {entry.actorType}
                  {entry.actorId ? ` · ${entry.actorId}` : ''}
                </TableCell>
                <TableCell className="text-muted-foreground" onClick={(e) => e.stopPropagation()}>
                  {link ? (
                    <Link
                      to={link}
                      className="text-info underline"
                      data-testid={`audit-entity-link-${entry.eventId}`}
                    >
                      {entry.targetEntityType} · {entry.targetEntityId}
                    </Link>
                  ) : (
                    <span>
                      {entry.targetEntityType}
                      {entry.targetEntityId ? ` · ${entry.targetEntityId}` : ''}
                    </span>
                  )}
                </TableCell>
              </TableRow>
              {isExpanded && (
                <TableRow data-testid={`audit-detail-panel-${entry.eventId}`}>
                  <TableCell colSpan={4} className="bg-table-hover">
                    <pre
                      className="font-mono text-xs whitespace-pre-wrap text-muted-foreground"
                      data-testid={`audit-payload-${entry.eventId}`}
                    >
                      {JSON.stringify(entry.payload, null, 2)}
                    </pre>
                  </TableCell>
                </TableRow>
              )}
            </Fragment>
          );
        })}
      </TableBody>
    </Table>
  );
}
