import { Inject, Injectable, Logger } from '@nestjs/common';
import { CACHE_MANAGER } from '@nestjs/cache-manager';
import type { Cache } from 'cache-manager';

/**
 * Cache service for managing Redis-backed caching operations.
 *
 * This service provides a wrapper around the NestJS cache manager with
 * additional error handling and logging capabilities. It uses Redis as
 * the underlying cache store for distributed caching across instances.
 *
 * @example
 * ```typescript
 * // Get cached value
 * const user = await cacheService.get<User>('user:123');
 *
 * // Set with TTL (in seconds)
 * await cacheService.set('user:123', user, 300);
 *
 * // Delete by pattern
 * await cacheService.delPattern('user:*');
 * ```
 */
@Injectable()
export class CacheService {
  private readonly logger = new Logger(CacheService.name);

  constructor(@Inject(CACHE_MANAGER) private cacheManager: Cache) {}

  /**
   * Retrieves a cached value by key.
   *
   * @template T - The expected type of the cached value
   * @param key - The cache key to look up
   * @returns The cached value or undefined if not found or on error
   */
  async get<T>(key: string): Promise<T | undefined> {
    try {
      return await this.cacheManager.get<T>(key);
    } catch (error) {
      this.logger.error(`Error getting cache key ${key}:`, error);
      return undefined;
    }
  }

  /**
   * Stores a value in cache with an optional TTL.
   *
   * @template T - The type of the value to cache
   * @param key - The cache key
   * @param value - The value to store
   * @param ttl - Time to live in seconds (uses default if not specified)
   */
  async set<T>(key: string, value: T, ttl?: number): Promise<void> {
    try {
      await this.cacheManager.set(key, value, ttl);
    } catch (error) {
      this.logger.error(`Error setting cache key ${key}:`, error);
    }
  }

  /**
   * Deletes a single cache entry by key.
   *
   * @param key - The cache key to delete
   */
  async del(key: string): Promise<void> {
    try {
      await this.cacheManager.del(key);
    } catch (error) {
      this.logger.error(`Error deleting cache key ${key}:`, error);
    }
  }

  /**
   * Deletes all cache entries matching a glob pattern.
   *
   * Uses Redis KEYS command to find matching keys, then deletes them all.
   * Use with caution on large datasets as KEYS is O(N).
   *
   * @param pattern - Glob pattern to match (e.g., 'user:*', 'problem:*')
   */
  async delPattern(pattern: string): Promise<void> {
    try {
      const cacheStore = this.cacheManager as unknown as {
        store: { keys: (pattern: string) => Promise<string[]> };
      };
      const keys = await cacheStore.store.keys?.(pattern);
      if (keys && keys.length > 0) {
        await Promise.all(keys.map((key) => this.cacheManager.del(key)));
        this.logger.debug(
          `Deleted ${keys.length} keys matching pattern: ${pattern}`,
        );
      }
    } catch (error) {
      this.logger.error(`Error deleting cache pattern ${pattern}:`, error);
    }
  }

  /**
   * Clears all cache entries.
   *
   * WARNING: This will delete ALL cached data. Use with caution.
   */
  async clear(): Promise<void> {
    try {
      // cache-manager doesn't have a built-in reset method, use delPattern instead
      await this.delPattern('*');
      this.logger.log('Cache cleared');
    } catch (error) {
      this.logger.error('Error clearing cache:', error);
    }
  }
}
