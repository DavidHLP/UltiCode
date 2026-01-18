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

    // Use atomic transaction to ensure both hset and expire succeed together
    const results = await this.redis
      .multi()
      .hset(key, 'token', token)
      .expire(key, this.TOKEN_TTL)
      .exec();

    // Check if transaction succeeded
    if (!results || results.some(([err]) => err)) {
      this.logger.error(
        `[CSRF_WRITE_FAIL] userId=${userId}, key=${key}, transaction failed`,
      );
      throw new Error(`Failed to store CSRF token for user ${userId}`);
    }

    const tokenPrefix = token.substring(0, 8);
    this.logger.log(
      `[CSRF_WRITE_SUCCESS] userId=${userId}, key=${key}, token=${tokenPrefix}..., ttl=${this.TOKEN_TTL}s`,
    );
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
    const tokenPrefix = token.substring(0, 8);

    this.logger.log(
      `[CSRF_VALIDATE_START] userId=${userId}, key=${key}, providedToken=${tokenPrefix}...`,
    );

    const storedToken = await this.redis.hget(key, 'token');

    if (storedToken) {
      const storedPrefix = storedToken.substring(0, 8);
      this.logger.log(
        `[CSRF_VALIDATE_REDIS] userId=${userId}, storedToken=${storedPrefix}..., keyExists=true`,
      );
    } else {
      this.logger.warn(
        `[CSRF_VALIDATE_REDIS] userId=${userId}, keyExists=false, token not found in Redis`,
      );
    }

    const isValid = storedToken === token;
    if (!isValid) {
      this.logger.error(
        `[CSRF_VALIDATE_FAIL] userId=${userId}, providedToken=${tokenPrefix}..., storedToken=${storedToken?.substring(0, 8) || 'null'}..., mismatch=true`,
      );
    } else {
      this.logger.log(
        `[CSRF_VALIDATE_SUCCESS] userId=${userId}, token=${tokenPrefix}..., match=true`,
      );
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
    this.logger.log(`[CSRF_REVOKE] userId=${userId}, key=${key}`);
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
