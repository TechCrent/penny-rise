import { useQuery } from '@tanstack/react-query';
import { fetchSubscriptionStatus } from '../subscriptionApi';

export function useSubscriptionStatus() {
  return useQuery({
    queryKey: ['subscription', 'status'],
    queryFn: fetchSubscriptionStatus,
  });
}
