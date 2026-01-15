import { Injectable, Logger } from '@nestjs/common';
import { randomBytes } from 'crypto';
import { Inject } from '@nestjs/common';
import { REDIS_CONNECTION } from './token-blacklist.service';
import Redis from 'ioredis';

/**
 * CSRF Service - Generates, validates, and manages CSRF tokens using Redis
 *
 * This service implements a double-submit CSRF protection pattern where:
 * - Tokens are generated on the server and stored in Redis
 * - Tokens are returned to the client and stored in localStorage
 * - Client must include the token in X-CSRF-Token header for state-changing requests
 * - Server validates the token matches the stored value
 *
 * Security considerations:
 * - Uses 256-bit cryptographically secure random tokens
 * - Tokens are user-specific (different users have different tokens)
 * - Tokens expire automatically after 7 days (matching JWT expiry)
 * - Tokens are revoked on logout
 */
@Injectable()
export class CsrfService {
  private readonly logger = new Logger(CsrfService.name);
  private readonly TOKEN_TTL = 7 * 24 * 60 * 60; // 7 days in seconds
  private readonly TOKEN_LENGTH = 32; // 32 bytes = 256 bits

  constructor(@Inject(REDIS_CONNECTION) private readonly redis: Redis) {}

  /**
   * Generate a new CSRF token for a user
   * @param userId The user ID to associate the token with
   * @returns The generated CSRF token (64-character hex string)
   */
  async generateCsrfToken(userId: string): Promise<string> {
    const token = randomBytes(this.TOKEN_LENGTH).toString('hex');
    const key = this.getCsrfKey(userId);

    await this.redis.hset(key, 'token', token);
    await this.redis.expire(key, this.TOKEN_TTL);

    this.logger.debug(`Generated CSRF token for user ${userId}`);
    return token;
  }

  /**
   * Validate a CSRF token for a user
   * @param userId The user ID
   * @param token The CSRF token to validate
   * @returns True if the token is valid, false otherwise
   */
  async validateCsrfToken(userId: string, token: string): Promise<boolean> {
    const key = this.getCsrfKey(userId);
    const storedToken = await this.redis.hget(key, 'token');

    const isValid = storedToken === token;
    if (!isValid) {
      this.logger.warn(`CSRF validation failed for user ${userId}`);
    }

    return isValid;
  }

  /**
   * Revoke a CSRF token for a user
   * @param userId The user ID
   */
  async revokeCsrfToken(userId: string): Promise<void> {
    const key = this.getCsrfKey(userId);
    await this.redis.del(key);
    this.logger.debug(`Revoked CSRF token for user ${userId}`);
  }

  /**
   * Get the Redis key for storing a user's CSRF token
   * @param userId The user ID
   * @returns The Redis key
   */
  private getCsrfKey(userId: string): string {
    return `csrf:user:${userId}`;
  }
}
