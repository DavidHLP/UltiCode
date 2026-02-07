import { ref } from "vue";

/**
 * Simple LRU cache for code editor language switching
 *
 * Features:
 * - Maximum 10 entries (automatic eviction)
 * - O(1) get/set operations using Map
 * - TypeScript type safety
 * - Per-component lifecycle (clears on unmount)
 *
 * @param maxSize - Maximum number of cached entries (default: 10)
 * @returns Cache API with get/set/has/clear methods
 */
export function useCodeCache(maxSize: number = 10) {
  // Using Map for O(1) lookups and maintaining insertion order
  const cache = ref<Map<string, string>>(new Map());

  /**
   * Get cached code for a language and mark as recently used
   */
  const get = (key: string): string | undefined => {
    const value = cache.value.get(key);
    if (value !== undefined) {
      // Mark as recently used by re-inserting (Map maintains insertion order)
      cache.value.delete(key);
      cache.value.set(key, value);
    }
    return value;
  };

  /**
   * Set code for a language (triggers eviction if at capacity)
   */
  const set = (key: string, value: string): void => {
    // Remove existing entry if present (will be re-added to end)
    if (cache.value.has(key)) {
      cache.value.delete(key);
    }
    // Evict least recently used (first entry) if at capacity
    else if (cache.value.size >= maxSize) {
      const firstKey = cache.value.keys().next().value;
      if (firstKey !== undefined) {
        cache.value.delete(firstKey);
      }
    }
    cache.value.set(key, value);
  };

  /**
   * Check if language is cached
   */
  const has = (key: string): boolean => {
    return cache.value.has(key);
  };

  /**
   * Clear all cached entries
   */
  const clear = (): void => {
    cache.value.clear();
  };

  /**
   * Get current cache size (for debugging/testing)
   */
  const size = (): number => {
    return cache.value.size;
  };

  return {
    get,
    set,
    has,
    clear,
    size,
  };
}
