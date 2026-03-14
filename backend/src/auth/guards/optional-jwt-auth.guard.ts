import { CanActivate, ExecutionContext, Injectable } from '@nestjs/common';
import { Reflector, ModuleRef } from '@nestjs/core';
import { JwtService } from '@nestjs/jwt';
import { Request } from 'express';
import { UserService } from '../../user/user.service';
import { TokenBlacklistService } from '../token-blacklist.service';
import { extractTokenFromHeader } from '../auth.utils';

/**
 * Guard that optionally extracts user information from JWT token.
 * Unlike AuthGuard, this guard allows access even without authentication,
 * but will populate request.user if a valid token is present.
 */
@Injectable()
export class OptionalJwtAuthGuard implements CanActivate {
  private userService: UserService | null = null;
  private tokenBlacklistService: TokenBlacklistService | null = null;

  constructor(
    private reflector: Reflector,
    private jwtService: JwtService,
    private moduleRef: ModuleRef,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    // Lazy load services to avoid circular dependency
    if (!this.userService) {
      this.userService = this.moduleRef.get(UserService, { strict: false });
    }
    if (!this.tokenBlacklistService) {
      this.tokenBlacklistService = this.moduleRef.get(TokenBlacklistService, {
        strict: false,
      });
    }

    const request = context.switchToHttp().getRequest<Request>();

    // Try to get token from Authorization header first
    let token = extractTokenFromHeader(request);

    // If no token in header, try cookie (for browser clients)
    if (!token) {
      const cookies = request.cookies as { access_token?: string } | undefined;
      token = cookies?.access_token;
    }

    // If no token, allow access but without user info
    if (!token) {
      return true;
    }

    try {
      // Check if token is blacklisted (revoked)
      const isBlacklisted =
        await this.tokenBlacklistService!.isBlacklisted(token);
      if (isBlacklisted) {
        // Token is blacklisted, allow access but don't set user
        return true;
      }

      const payload = await this.jwtService.verifyAsync<{
        sub: string;
        username: string;
        role: string;
      }>(token);

      if (payload?.sub) {
        const user = await this.userService!.findOne(payload.sub);
        if (user) {
          request['user'] = { id: user.id };
        }
      }
    } catch {
      // Token verification failed, but allow access anyway
      // This is optional auth - we don't throw UnauthorizedException
    }

    return true;
  }
}
