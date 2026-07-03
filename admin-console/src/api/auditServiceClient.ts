import axios from 'axios';

// Audit-service is a separate microservice on its own port — confirmed via
// docker-compose/application.yml, no gateway or reverse-proxy unifies it
// with the monolith or kyc-service, so this needs its own base URL and its
// own axios instance (the existing adminApiClient in client.ts is fixed to
// VITE_KYC_API_URL ?? VITE_API_URL, neither of which is audit-service).
const BASE_URL = import.meta.env.VITE_AUDIT_API_URL ?? 'http://localhost:8083';

export const auditServiceClient = axios.create({
  baseURL: BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

auditServiceClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('stash_admin_token');
  if (token) {
    // audit-service's AuditAdminJwtAuthenticationFilter only reads
    // Authorization: Bearer — see client.ts's interceptor for the fuller
    // explanation of why this differs from kyc-service's convention.
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});
