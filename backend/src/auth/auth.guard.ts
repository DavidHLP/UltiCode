import {
  CanActivate,
  ExecutionContext,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { Reflector, ModuleRef } from '@nestjs/core';
import { IS_PUBLIC_KEY } from './auth.decorator';
import { JwtService } from '@nestjs/jwt';
import { Request } from 'express';
import { UserService } from '../user/user.service';
import { TokenBlacklistService } from './token-blacklist.service';
import { extractTokenFromHeader } from './auth.utils';

@Injectable()
export class AuthGuard implements CanActivate {
  private userService: UserService;
  private tokenBlacklistService: TokenBlacklistService;

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

    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    const request = context.switchToHttp().getRequest<Request>();

    // Try to get token from Authorization header first
    let token = extractTokenFromHeader(request);

    // If no token in header, try cookie (for browser clients)
    if (!token) {
      const cookies = request.cookies as { access_token?: string } | undefined;
      token = cookies?.access_token;
    }

    if (token) {
      try {
        // Check if token is blacklisted (revoked)
        const isBlacklisted =
          await this.tokenBlacklistService.isBlacklisted(token);
        if (isBlacklisted) {
          throw new UnauthorizedException('Token has been revoked');
        }

        const payload = await this.jwtService.verifyAsync<{
          sub: string;
          username: string;
          role: string;
        }>(token);
        if (payload?.sub) {
          const user = await this.userService.findOne(payload.sub);
          if (user) {
            request['user'] = user;
          }
        }
      } catch {
        // If token is invalid but route is public, ignore error
        if (!isPublic) {
          throw new UnauthorizedException();
        }
      }
    } else if (!isPublic) {
      throw new UnauthorizedException();
    }

    return true;
  }
}
