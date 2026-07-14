import { describe, it, expect, beforeEach } from 'vitest';
import { adminApiClient } from '../src/api/client';

// v0.5-033: this interceptor previously only sent X-Admin-Token, a header
// neither monolith's AdminJwtAuthenticationFilter nor audit-service's
// AuditAdminJwtAuthenticationFilter ever checked (both only read
// Authorization: Bearer) — every admin-console call to either backend was
// silently unauthenticated. kyc-service's PlaceholderAdminAuthFilter still
// only checks X-Admin-Token, so both headers are sent now.
describe('adminApiClient request interceptor', () => {
  beforeEach(() => {
    sessionStorage.clear();
  });

  it('attaches both Authorization: Bearer and X-Admin-Token when a token is stored', async () => {
    sessionStorage.setItem('pennyrise_admin_token', 'test-token-123');

    // @ts-expect-error — accessing axios's internal interceptor handler array to invoke it directly
    const fulfilled = adminApiClient.interceptors.request.handlers[0].fulfilled;
    const result = await fulfilled({ headers: {} });

    expect(result.headers.Authorization).toBe('Bearer test-token-123');
    expect(result.headers['X-Admin-Token']).toBe('test-token-123');
  });

  it('sends no auth headers when no token is stored', async () => {
    // @ts-expect-error — accessing axios's internal interceptor handler array to invoke it directly
    const fulfilled = adminApiClient.interceptors.request.handlers[0].fulfilled;
    const result = await fulfilled({ headers: {} });

    expect(result.headers.Authorization).toBeUndefined();
    expect(result.headers['X-Admin-Token']).toBeUndefined();
  });
});
