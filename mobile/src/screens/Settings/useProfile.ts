import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchProfile, updateProfile, type UpdateProfileRequest } from '../../api/profileApi';

const PROFILE_KEY = ['profile'] as const;

export function useProfile() {
  const queryClient = useQueryClient();

  const profileQuery = useQuery({
    queryKey: PROFILE_KEY,
    queryFn: fetchProfile,
  });

  const updateMutation = useMutation({
    mutationFn: (request: UpdateProfileRequest) => updateProfile(request),
    onSuccess: updated => {
      queryClient.setQueryData(PROFILE_KEY, updated);
    },
  });

  return {
    profileQuery,
    update: updateMutation.mutate,
    isUpdating: updateMutation.isPending,
    updateError: updateMutation.error,
    updateSuccess: updateMutation.isSuccess,
  };
}
