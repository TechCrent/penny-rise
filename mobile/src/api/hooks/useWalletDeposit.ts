import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../client';
import type { DepositOtpPayload, DepositPayload, DepositResponse } from './useVaultDeposit';

export function useWalletDeposit() {
  const queryClient = useQueryClient();
  return useMutation<DepositResponse, Error, { payload: DepositPayload; idempotencyKey: string }>({
    mutationFn: async ({ payload, idempotencyKey }) => {
      const { data } = await apiClient.post<DepositResponse>('/api/v1/users/me/deposits', payload, {
        headers: { 'Idempotency-Key': idempotencyKey },
      });
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wallet-balance'] });
      queryClient.invalidateQueries({ queryKey: ['statement'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
    },
  });
}

export function useWalletDepositOtp() {
  const queryClient = useQueryClient();
  return useMutation<
    DepositResponse,
    Error,
    { transactionReference: string; payload: DepositOtpPayload; idempotencyKey: string }
  >({
    mutationFn: async ({ transactionReference, payload, idempotencyKey }) => {
      const { data } = await apiClient.post<DepositResponse>(
        `/api/v1/users/me/deposits/${transactionReference}/otp`,
        payload,
        { headers: { 'Idempotency-Key': idempotencyKey } },
      );
      return data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wallet-balance'] });
      queryClient.invalidateQueries({ queryKey: ['statement'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
    },
  });
}
