import type { AdminDisputeDetail } from '../../api/disputesAdmin';

/**
 * Maps relatedEntityType (the real RelatedEntityType enum:
 * TRANSACTION/SUSU_GROUP/TRANSFER/ACCOUNT) to an admin-console route.
 * Mirrors the mobile app's deepLinkRouter.ts pattern (v0.5-021) — type-
 * driven navigation, not URL parsing.
 *
 * Only ACCOUNT resolves today, to the user detail page (v0.5-025). No
 * admin transaction, susu group, or transfer detail page exists anywhere
 * in this app yet, so those fall through to no link rather than a broken one.
 */
export function relatedEntityLink(dispute: AdminDisputeDetail): string | null {
  switch (dispute.relatedEntityType) {
    case 'ACCOUNT':
      return `/users/${dispute.relatedEntityId}`;
    case 'TRANSACTION':
    case 'SUSU_GROUP':
    case 'TRANSFER':
      return null;
    default:
      return null;
  }
}
