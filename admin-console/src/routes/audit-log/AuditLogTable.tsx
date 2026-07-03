import { Fragment, useState } from 'react';
import { format } from 'date-fns';
import { Link } from 'react-router-dom';
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
        className="bg-white rounded-xl border border-slate-200 p-12 text-center"
        data-testid="audit-log-empty-state"
      >
        <p className="text-slate-400 text-lg">No audit entries match the current filters.</p>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl border border-slate-200 overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50">
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Occurred At
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Event
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Actor
            </th>
            <th className="text-left px-6 py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Target
            </th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => {
            const isExpanded = expandedId === entry.eventId;
            const link = auditEntityLink(entry);

            return (
              <Fragment key={entry.eventId}>
                <tr
                  className="border-b border-slate-100 hover:bg-slate-50 transition-colors cursor-pointer"
                  onClick={() => setExpandedId(isExpanded ? null : entry.eventId)}
                  data-testid={`audit-row-${entry.eventId}`}
                >
                  <td className="px-6 py-4 text-slate-600">
                    {format(new Date(entry.occurredAt), 'dd MMM yyyy, HH:mm:ss')}
                  </td>
                  <td className="px-6 py-4">
                    <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700">
                      {entry.eventType}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-slate-600">
                    {entry.actorType}
                    {entry.actorId ? ` · ${entry.actorId}` : ''}
                  </td>
                  <td className="px-6 py-4 text-slate-600" onClick={(e) => e.stopPropagation()}>
                    {link ? (
                      <Link
                        to={link}
                        className="text-blue-600 underline"
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
                  </td>
                </tr>
                {isExpanded && (
                  <tr data-testid={`audit-detail-panel-${entry.eventId}`}>
                    <td colSpan={4} className="bg-slate-50 px-6 py-4">
                      <pre
                        className="text-xs whitespace-pre-wrap font-mono text-slate-700"
                        data-testid={`audit-payload-${entry.eventId}`}
                      >
                        {JSON.stringify(entry.payload, null, 2)}
                      </pre>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
