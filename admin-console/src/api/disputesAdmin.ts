import { adminApiClient } from './client';

// LOW/NORMAL/HIGH/CRITICAL is the real admin.disputes.priority CHECK
// constraint (V22__create_disputes.sql) — there is no URGENT value anywhere
// in the schema.
export type DisputePriority = 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
export type DisputeStatus = 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'CLOSED_NO_ACTION';
export type RelatedEntityType = 'TRANSACTION' | 'SUSU_GROUP' | 'TRANSFER' | 'ACCOUNT';

export interface AdminDisputeListItem {
  id: string;
  // Raw UUID only — verified against the real AdminDisputeListItem DTO,
  // which does not join to user_module.users for a display name or email.
  raisedByUserId: string;
  disputeType: string;
  relatedEntityType: RelatedEntityType;
  relatedEntityId: string;
  subject: string;
  status: DisputeStatus;
  priority: DisputePriority;
  assignedToAdminId: string | null;
  createdAt: string;
}

export interface AdminDisputeListResponse {
  disputes: AdminDisputeListItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AssignmentHistoryEntry {
  adminId: string;
  adminName: string;
  assignedAt: string;
}

// Proposed — GET /api/v1/admin/disputes/{id} does not exist anywhere in the
// backend today (confirmed: AdminDisputeController only has GET list,
// POST /assign, POST /resolve, POST /close-no-action). assignmentHistory is
// proposed as derived from admin.admin_audit_actions rows with
// action_type='DISPUTE_ASSIGNED' filtered to this dispute's target_id —
// that data already exists, just not exposed via a read endpoint.
export interface AdminDisputeDetail extends AdminDisputeListItem {
  description: string;
  assignmentHistory: AssignmentHistoryEntry[];
}

export interface DisputeQueueParams {
  status?: string;
  page?: number;
  size?: number;
}

export async function fetchDisputeQueue(
  params: DisputeQueueParams,
): Promise<AdminDisputeListResponse> {
  const { data } = await adminApiClient.get<AdminDisputeListResponse>('/api/v1/admin/disputes', {
    params: {
      status: params.status || undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
    },
  });
  return data;
}

export async function fetchDisputeDetail(id: string): Promise<AdminDisputeDetail> {
  const { data } = await adminApiClient.get<AdminDisputeDetail>(`/api/v1/admin/disputes/${id}`);
  return data;
}

export async function assignDispute(id: string): Promise<void> {
  await adminApiClient.post(`/api/v1/admin/disputes/${id}/assign`);
}

export async function resolveDispute(id: string, notes: string): Promise<void> {
  // resolution is JsonNode server-side (@NotNull, no enforced shape) — the
  // migration comment's intended shape includes a "category" field
  // (REFUNDED/REVERSED/ESCALATED/...), but nothing in code requires it, and
  // this UI only collects free-text notes.
  await adminApiClient.post(`/api/v1/admin/disputes/${id}/resolve`, {
    resolution: { notes },
  });
}

export async function closeDisputeNoAction(id: string, reason: string): Promise<void> {
  await adminApiClient.post(`/api/v1/admin/disputes/${id}/close-no-action`, { reason });
}

export interface BulkActionItemResult {
  id: string;
  success: boolean;
  error_message: string | null;
}

export interface BulkActionResultResponse {
  results: BulkActionItemResult[];
}

/** Applies the same resolution notes to every dispute in `ids`. */
export async function bulkResolveDisputes(
  ids: string[],
  notes: string,
): Promise<BulkActionResultResponse> {
  const { data } = await adminApiClient.post<BulkActionResultResponse>(
    '/api/v1/admin/disputes/bulk-resolve',
    { ids, resolution: { notes } },
  );
  return data;
}
