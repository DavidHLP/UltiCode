import { ref, onMounted, type Ref } from "vue";

/**
 * Stale-While-Revalidate cache entry.
 * Returns stale data immediately, refreshes in background.
 */
interface CacheEntry<T> {
  data: T;
  timestamp: number;
}

interface SWROptions<T> {
  /** Cache key (must be unique per data type) */
  key: string;
  /** Data fetcher function */
  fetcher: () => Promise<T>;
  /** Time-to-live in ms (default: 5 min) */
  ttl?: number;
  /** Whether to revalidate when stale (default: true) */
  revalidateOnStale?: boolean;
}

// Shared in-memory cache (persists across component lifecycle within session)
const cache = new Map<string, CacheEntry<unknown>>();

/**
 * Remove a specific cache entry.
 * Useful for invalidation after mutations.
 */
export function invalidateSWR(key: string): void {
  cache.delete(key);
}

/**
 * Remove all cache entries matching a prefix.
 * Example: invalidateSWRByPrefix("rec:") clears all recommendation caches.
 */
export function invalidateSWRByPrefix(prefix: string): void {
  for (const key of cache.keys()) {
    if (key.startsWith(prefix)) {
      cache.delete(key);
    }
  }
}

/**
 * SWR (Stale-While-Revalidate) composable.
 *
 * - Returns cached data immediately if available (even if stale)
 * - Refreshes in background when TTL expires
 * - Tracks loading/error state
 * - Integrates with existing useRetry in request.ts
 */
export function useSWR<T>(options: SWROptions<T>): {
  data: Ref<T | null>;
  error: Ref<Error | null>;
  loading: Ref<boolean>;
  refresh: () => Promise<void>;
} {
  const { key, fetcher, ttl = 5 * 60 * 1000, revalidateOnStale = true } = options;

  const data = ref<T | null>(null) as Ref<T | null>;
  const error = ref<Error | null>(null);
  const loading = ref(false);

  const stale = false;

  function isExpired(entry: CacheEntry<unknown>): boolean {
    return Date.now() - entry.timestamp > ttl;
  }

  async function refresh(): Promise<void> {
    const cached = cache.get(key);

    // Fresh cache — return immediately
    if (cached && !isExpired(cached) && !stale) {
      data.value = cached.data as T;
      return;
    }

    // Stale cache — serve old data, refresh in background
    if (cached && revalidateOnStale) {
      data.value = cached.data as T;
      try {
        const fresh = await fetcher();
        data.value = fresh;
        cache.set(key, { data: fresh, timestamp: Date.now() });
        error.value = null;
      } catch (e) {
        error.value = e as Error;
        // Keep stale data visible — user can still interact
      }
      return;
    }

    // No cache — full load
    loading.value = true;
    try {
      const result = await fetcher();
      data.value = result;
      cache.set(key, { data: result, timestamp: Date.now() });
      error.value = null;
    } catch (e) {
      error.value = e as Error;
    } finally {
      loading.value = false;
    }
  }

  onMounted(refresh);

  return { data, error, loading, refresh };
}
