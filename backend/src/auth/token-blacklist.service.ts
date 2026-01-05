import { Injectable, OnModuleDestroy } from '@nestjs/common';
import { createHash } from 'crypto';
import Redis from 'ioredis';

/**
 * Token Blacklist Service
 *
 * Stores revoked JWT tokens in Redis with TTL matching the token's expiration.
 * This prevents tokens from being used after logout.
 *
 * Uses a dedicated Redis connection separate from BullMQ to avoid conflicts.
 */
@Injectable()
export class TokenBlacklistService implements OnModuleDestroy {
  private redis: Redis;

  /**
   * The default token expiration time (7 days) in seconds
   * This should match the JWT_EXPIRES_IN in auth.module.ts
   */
  private readonly DEFAULT_TTL = 7 * 24 * 60 * 60; // 7 days in seconds

  constructor() {
    // Create a dedicated Redis connection for the token blacklist
    // Reads connection settings from environment variables
    this.redis = new Redis({
      host: process.env.REDIS_HOST || 'localhost',
      port: parseInt(process.env.REDIS_PORT || '6379'),
      password: process.env.REDIS_PASSWORD || undefined,
      maxRetriesPerRequest: 3,
      retryStrategy: (times) => {
        const delay = Math.min(times * 50, 2000);
        return delay;
      },
    });
  }

  async onModuleDestroy() {
    // Close the Redis connection when the module is destroyed
    await this.redis.quit();
  }

  /**
   * Add a token to the blacklist
   * @param token The JWT token to blacklist
   * @param ttl Optional TTL in seconds (defaults to 7 days)
   */
  async addToBlacklist(token: string, ttl?: number): Promise<void> {
    const key = this.getBlacklistKey(token);
    const expiry = ttl || this.DEFAULT_TTL;

    // Store in Redis with automatic expiration
    await this.redis.set(key, '1', 'EX', expiry);
  }

  /**
   * Check if a token is blacklisted
   * @param token The JWT token to check
   * @returns true if the token is blacklisted
   */
  async isBlacklisted(token: string): Promise<boolean> {
    const key = this.getBlacklistKey(token);
    const result = await this.redis.get(key);
    return result !== null;
  }

  /**
   * Get the Redis key for a blacklisted token
   * Uses a hash of the token to avoid storing the raw token
   */
  private getBlacklistKey(token: string): string {
    // Use crypto for hashing - Node.js built-in
    const hash = createHash('sha256').update(token).digest('hex');
    return `blacklist:token:${hash}`;
  }

  /**
   * Remove a token from the blacklist (e.g., for testing)
   * @param token The JWT token to remove
   */
  async removeFromBlacklist(token: string): Promise<void> {
    const key = this.getBlacklistKey(token);
    await this.redis.del(key);
  }
}
