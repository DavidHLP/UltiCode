import { Injectable } from '@nestjs/common';
import { randomBytes } from 'crypto';
import { PrismaService } from '../prisma.service';

@Injectable()
export class RefreshTokenService {
  constructor(private prisma: PrismaService) {}

  /**
   * Generate a cryptographically secure refresh token
   */
  generateToken(): string {
    return randomBytes(32).toString('hex');
  }

  /**
   * Create a refresh token for a user
   * @param userId The user ID
   * @param expiresIn Token expiration time in milliseconds (default: 7 days)
   */
  async createRefreshToken(
    userId: string,
    expiresIn: number = 7 * 24 * 60 * 60 * 1000,
  ) {
    const token = this.generateToken();
    const expiresAt = new Date(Date.now() + expiresIn);

    return this.prisma.refreshToken.create({
      data: {
        user_id: userId,
        token,
        expires_at: expiresAt,
      },
    });
  }

  /**
   * Validate a refresh token
   * @param token The refresh token to validate
   * @returns The refresh token record with user, or null if invalid
   */
  async validateRefreshToken(token: string) {
    const refreshToken = await this.prisma.refreshToken.findUnique({
      where: { token },
      include: { user: true },
    });

    if (!refreshToken) {
      return null;
    }

    // Check if revoked
    if (refreshToken.is_revoked) {
      return null;
    }

    // Check if expired
    if (new Date() > refreshToken.expires_at) {
      return null;
    }

    return refreshToken;
  }

  /**
   * Rotate a refresh token (create new, revoke old)
   * @param oldToken The old refresh token to rotate
   * @returns The new refresh token record, or null if old token was invalid
   */
  async rotateRefreshToken(oldToken: string) {
    const oldRecord = await this.validateRefreshToken(oldToken);

    if (!oldRecord) {
      return null;
    }

    // Revoke old token
    await this.revokeRefreshToken(oldToken);

    // Create new token
    return this.createRefreshToken(oldRecord.user_id);
  }

  /**
   * Revoke a specific refresh token
   * @param token The refresh token to revoke
   */
  async revokeRefreshToken(token: string) {
    return this.prisma.refreshToken.update({
      where: { token },
      data: {
        is_revoked: true,
        revoked_at: new Date(),
      },
    });
  }

  /**
   * Revoke all refresh tokens for a user
   * @param userId The user ID
   */
  async revokeAllUserTokens(userId: string) {
    return this.prisma.refreshToken.updateMany({
      where: { user_id: userId },
      data: {
        is_revoked: true,
        revoked_at: new Date(),
      },
    });
  }

  /**
   * Clean up expired tokens (for cron job or scheduled task)
   * @returns The number of deleted tokens
   */
  async cleanupExpiredTokens() {
    const result = await this.prisma.refreshToken.deleteMany({
      where: {
        expires_at: { lt: new Date() },
      },
    });
    return result.count;
  }
}
