// POST and DELETE /api/v1/users/me/deletion-request are real, already-shipped
// endpoints (v0.2-019, UserController.java) — their DTO uses explicit
// snake_case @JsonProperty annotations, not a global naming strategy, so
// these field names are verified against the real DeletionRequestResponse,
// not assumed. GET (status) and GET .../deletion-blockers do not exist yet
// — see api/deletionApi.ts and the PR notes for the proposed contract,
// designed to match this same snake_case convention since it lives on the
// same controller/table.

export type DeletionRequestStatusValue = 'PENDING' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

export interface DeletionRequestStatus {
  id: string;
  status: DeletionRequestStatusValue;
  submitted_at: string;
  scheduled_completion_at: string;
}

export interface DeletionBlocker {
  type: string; // e.g. "OPEN_SUSU_GROUP", "ACTIVE_LOCKED_VAULT" — server-supplied category
  description: string; // human-readable, server-supplied
}

export type DeletionScreenState = 'LOADING' | 'PRE_SUBMISSION' | 'COOL_OFF';
