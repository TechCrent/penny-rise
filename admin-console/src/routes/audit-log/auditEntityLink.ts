import type { AuditLogEntry } from '../../api/auditLogAdmin';

/**
 * target_entity_type is a deliberately open vocabulary at the DB level
 * (Schema doc §9.1, no CHECK constraint) — only USER is confirmed to
 * resolve to a real admin page today (/users/:userId, v0.5-025). No admin
 * transaction detail page exists anywhere in this app yet (same gap
 * routes/disputes/relatedEntityLink.ts already documents for its own
 * TRANSACTION/SUSU_GROUP cases) — everything else falls through to no
 * link rather than a broken one.
 */
export function auditEntityLink(entry: AuditLogEntry): string | null {
  if (!entry.targetEntityId) return null;

  switch (entry.targetEntityType) {
    case 'USER':
      return `/users/${entry.targetEntityId}`;
    default:
      return null;
  }
}
