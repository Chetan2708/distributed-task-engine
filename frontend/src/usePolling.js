import { useState, useEffect, useRef, useCallback } from 'react';

export function usePolling(fetchCallback, intervalMs = 2000, enabled = true) {
  const [lastUpdated, setLastUpdated] = useState(Date.now());
  const [isRefreshing, setIsRefreshing] = useState(false);
  const savedCallback = useRef(fetchCallback);

  useEffect(() => {
    savedCallback.current = fetchCallback;
  }, [fetchCallback]);

  const execute = useCallback(async () => {
    if (!savedCallback.current) return;
    try {
      setIsRefreshing(true);
      await savedCallback.current();
      setLastUpdated(Date.now());
    } catch (err) {
      console.error('Polling error:', err);
    } finally {
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    if (!enabled || intervalMs <= 0) return;

    // Run immediately on enable if needed
    execute();

    const id = setInterval(() => {
      execute();
    }, intervalMs);

    return () => clearInterval(id);
  }, [enabled, intervalMs, execute]);

  return {
    lastUpdated,
    isRefreshing,
    refreshNow: execute,
  };
}
