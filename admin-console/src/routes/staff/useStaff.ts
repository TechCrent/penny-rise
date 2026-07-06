import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  fetchStaffList,
  createStaffAccount,
  deactivateStaffAccount,
  type CreateAdminStaffRequest,
} from '../../api/staffAdmin';

export function useStaff(page: number) {
  const queryClient = useQueryClient();
  const listKey = ['adminStaff', page];

  const listQuery = useQuery({
    queryKey: listKey,
    queryFn: () => fetchStaffList(page),
    placeholderData: (prev) => prev,
  });

  const createMutation = useMutation({
    mutationFn: (request: CreateAdminStaffRequest) => createStaffAccount(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminStaff'] });
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => deactivateStaffAccount(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['adminStaff'] });
    },
  });

  return {
    listQuery,
    create: createMutation.mutate,
    isCreating: createMutation.isPending,
    createError: createMutation.error,
    deactivate: deactivateMutation.mutate,
    deactivatingId: deactivateMutation.variables,
    isDeactivating: deactivateMutation.isPending,
  };
}
