// Mirrors GET /api/v1/notifications's response shape from v0.5-015.
// Field names are kept snake_case (not mapped to camelCase) to match this
// codebase's convention for API-shaped types — see src/api/vaults.ts and
// src/api/payments.ts's StatementEntry/StatementPage.

export type NotificationChannel = 'PUSH' | 'EMAIL' | 'IN_APP';

export interface NotificationItem {
  id: string;
  type: string; // e.g. "DEPOSIT_SUCCESS", "KYC_APPROVED" — see v0.5-013's template registry for the full set
  title: string;
  body: string;
  data: Record<string, unknown>;
  channel: NotificationChannel;
  read_at: string | null; // ISO 8601, or null if unread
  created_at: string;
}

export interface NotificationInboxPage {
  notifications: NotificationItem[];
  next_cursor: string | null;
  has_more: boolean;
  unread_count: number;
}
