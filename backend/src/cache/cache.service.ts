import { Inject, Injectable, Logger } from '@nestjs/common';
import { CACHE_MANAGER } from '@nestjs/cache-manager';
import type { Cache } from 'cache-manager';

@Injectable()
export class CacheService {
  private readonly logger = new Logger(CacheService.name);

  constructor(@Inject(CACHE_MANAGER) private cacheManager: Cache) {}

  async get<T>(key: string): Promise<T | undefined> {
    try {
      return await this.cacheManager.get<T>(key);
    } catch (error) {
      this.logger.error(`Error getting cache key ${key}:`, error);
      return undefined;
    }
  }

  async set<T>(key: string, value: T, ttl?: number): Promise<void> {
    try {
      await this.cacheManager.set(key, value, ttl);
    } catch (error) {
      this.logger.error(`Error setting cache key ${key}:`, error);
    }
  }

  async del(key: string): Promise<void> {
    try {
      await this.cacheManager.del(key);
    } catch (error) {
      this.logger.error(`Error deleting cache key ${key}:`, error);
    }
  }

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
