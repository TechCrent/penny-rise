import { adminApiClient } from './client';

export interface QueueItem {
  submission_id: string;
  user_id: string;
  full_name_on_card: string;
  submitted_at: string;
  flag_reason: string | null;
  document_view_urls: Record<string, string>;
}

export interface QueueResponse {
  items: QueueItem[];
  next_cursor: string | null;
  has_more: boolean;
}

export interface ProviderDecisionSummary {
  decision: string;
  confidence_score: number | null;
  requested_at: string;
}

export interface SubmissionDetail {
  id: string;
  user_id: string;
  ghana_card_number: string;
  full_name_on_card: string;
  date_of_birth: string | null;
  phone_number: string | null;
  status: string;
  review_path: string | null;
  submitted_at: string;
  document_view_urls: Record<string, string>;
  provider_decisions: ProviderDecisionSummary[];
}

export async function fetchQueue(cursor?: string): Promise<QueueResponse> {
  const params = new URLSearchParams({ pageSize: '20' });
  if (cursor) params.set('cursor', cursor);
  const { data } = await adminApiClient.get<QueueResponse>(`/api/v1/kyc/admin/queue?${params}`);
  return data;
}

export async function fetchSubmissionDetail(id: string): Promise<SubmissionDetail> {
  const { data } = await adminApiClient.get<SubmissionDetail>(
    `/api/v1/kyc/admin/submissions/${id}`,
  );
  return data;
}

export async function approveSubmission(id: string): Promise<void> {
  await adminApiClient.post(`/api/v1/kyc/admin/submissions/${id}/approve`);
}

export async function rejectSubmission(id: string, reason: string): Promise<void> {
  await adminApiClient.post(`/api/v1/kyc/admin/submissions/${id}/reject`, { reason });
}

export function maskGhanaCardNumber(raw: string): string {
  if (!raw) return '—';
  const parts = raw.split('-');
  if (parts.length !== 3 || parts[0] !== 'GHA') return '****';
  const digits = parts[1];
  const masked = '*'.repeat(Math.max(0, digits.length - 4)) + digits.slice(-4);
  return `${parts[0]}-${masked}-${parts[2]}`;
}
