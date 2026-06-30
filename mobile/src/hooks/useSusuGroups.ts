import { useState, useCallback } from 'react';
import { susuApi } from '../api/susu';
import type { SusuGroupListResponse } from '../types/susu';

export function useSusuGroups() {
  const [groups, setGroups]         = useState<SusuGroupListResponse[]>([]);
  const [loading, setLoading]       = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError]           = useState<string | null>(null);

  const fetch = useCallback(async (isRefresh = false) => {
    if (isRefresh) setRefreshing(true);
    else           setLoading(true);
    setError(null);
    try {
      const data = await susuApi.listGroups(false);
      setGroups(data);
    } catch (e: any) {
      setError(e?.message ?? 'Failed to load susu groups.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  const refresh = useCallback(() => fetch(true), [fetch]);

  return { groups, loading, refreshing, error, fetch, refresh };
}
