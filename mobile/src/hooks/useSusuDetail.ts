import { useState, useCallback } from 'react';
import { susuApi } from '../api/susu';
import type { SusuGroupDetailResponse } from '../types/susu';

export function useSusuDetail(groupId: string) {
  const [group, setGroup] = useState<SusuGroupDetailResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetch = useCallback(
    async (isRefresh = false) => {
      if (isRefresh) setRefreshing(true);
      else setLoading(true);
      setError(null);
      try {
        const data = await susuApi.getGroupDetail(groupId);
        setGroup(data);
      } catch (e) {
        console.error(e);
        setError((e as Error)?.message ?? 'Failed to load group details.');
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [groupId],
  );

  const refresh = useCallback(() => fetch(true), [fetch]);

  return { group, loading, refreshing, error, fetch, refresh };
}
