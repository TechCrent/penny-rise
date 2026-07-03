import { useQuery } from '@tanstack/react-query';
import { searchUsers, type UserSearchParams } from '../../api/usersAdmin';

export function useUserSearch(params: UserSearchParams) {
  return useQuery({
    queryKey: ['adminUsers', 'search', params],
    queryFn: () => searchUsers(params),
    placeholderData: (prev) => prev,
  });
}
