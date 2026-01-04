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

@Injectable()
export class AuthGuard implements CanActivate {
  private userService: UserService;

  constructor(
    private reflector: Reflector,
    private jwtService: JwtService,
    private moduleRef: ModuleRef,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    if (!this.userService) {
      this.userService = this.moduleRef.get(UserService, { strict: false });
    }

    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    const request = context.switchToHttp().getRequest<Request>();
    const token = this.extractTokenFromHeader(request);

    if (token) {
      try {
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

  private extractTokenFromHeader(request: Request): string | undefined {
    // Check headers safely
    const [type, token] = (request.headers.authorization?.split(' ') ?? []) as [
      string | undefined,
      string | undefined,
    ];
    return type === 'Bearer' ? token : undefined;
  }
}
