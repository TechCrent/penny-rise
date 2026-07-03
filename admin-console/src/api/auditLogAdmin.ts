import { auditServiceClient } from './auditServiceClient';

// Field names verified directly against audit-service's real DTOs
// (AuditLogEntryResponse, AuditLogPageResponse, AuditLogFacetsResponse) —
// no @JsonProperty annotations and no global Jackson naming strategy exist
// there, so the wire format is camelCase exactly as declared in the Java
// records, not snake_case.
export interface AuditLogEntry {
  eventId: string;
  eventType: string;
  actorType: string;
  actorId: string | null;
  targetEntityType: string | null;
  targetEntityId: string | null;
  payload: unknown;
  occurredAt: string;
}

export interface AuditLogPageResponse {
  entries: AuditLogEntry[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface AuditLogFacets {
  eventTypes: string[];
  targetEntityTypes: string[];
}

export interface AuditLogFilterParams {
  actorId?: string;
  eventType?: string;
  targetEntityType?: string;
  fromDate?: string;
  toDate?: string;
  cursor?: string;
  limit?: number;
}

export async function fetchAuditLog(params: AuditLogFilterParams): Promise<AuditLogPageResponse> {
  const { data } = await auditServiceClient.get<AuditLogPageResponse>('/api/v1/admin/audit-log', {
    params: {
      actorId: params.actorId || undefined,
      eventType: params.eventType || undefined,
      targetEntityType: params.targetEntityType || undefined,
      fromDate: params.fromDate || undefined,
      toDate: params.toDate || undefined,
      cursor: params.cursor || undefined,
      limit: params.limit ?? 25,
    },
  });
  return data;
}

export async function fetchAuditLogFacets(): Promise<AuditLogFacets> {
  const { data } = await auditServiceClient.get<AuditLogFacets>('/api/v1/admin/audit-log/facets');
  return data;
}
