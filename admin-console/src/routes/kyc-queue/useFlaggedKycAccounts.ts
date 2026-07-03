import { useQuery } from '@tanstack/react-query';
import { searchUsers } from '../../api/usersAdmin';

/**
 * v0.5-035: reuses the existing GET /api/v1/admin/users?kyc_status=
 * filter from v0.5-005 rather than a new endpoint — the issue's own
 * description calls this "largely additive," confirmed by reading
 * AdminUserSearchCriteria/UserRepository.searchForAdmin, which already
 * support filtering by any kyc_status value including
 * RESUBMISSION_REQUIRED.
 */
export function useFlaggedKycAccounts(enabled: boolean) {
  return useQuery({
    queryKey: ['adminUsers', 'search', { kycStatus: 'RESUBMISSION_REQUIRED' }],
    queryFn: () => searchUsers({ kycStatus: 'RESUBMISSION_REQUIRED', page: 0, size: 100 }),
    enabled,
  });
}
