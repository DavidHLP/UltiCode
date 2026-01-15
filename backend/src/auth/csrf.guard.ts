import {
  Injectable,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
  Logger,
  SetMetadata,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { Request } from 'express';
import { CsrfService } from './csrf.service';
import { IS_PUBLIC_KEY } from './auth.decorator';

const SKIP_CSRF_KEY = 'skipCsrf';

/**
 * SkipCsrf decorator - Mark a route or controller to skip CSRF validation
 * Usage: @SkipCsrf()
 */
export const SkipCsrf = () => SetMetadata(SKIP_CSRF_KEY, true);

/**
 * CSRF Guard - Validates CSRF tokens on protected routes
 *
 * This guard:
 * - Skips validation for GET/HEAD/OPTIONS requests (safe methods)
 * - Skips validation for routes marked with @Public() or @SkipCsrf()
 * - Validates X-CSRF-Token header for state-changing requests
 * - Runs after AuthGuard (requires authenticated user)
 *
 * Usage: Apply to controllers/routes that need CSRF protection
 * @UseGuards(AuthGuard, CsrfGuard)
 */
@Injectable()
export class CsrfGuard implements CanActivate {
  private readonly logger = new Logger(CsrfGuard.name);

  constructor(
    private csrfService: CsrfService,
    private reflector: Reflector,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<Request>();

    // Check if route should skip CSRF validation
    const skipCsrf = this.reflector.getAllAndOverride<boolean>(SKIP_CSRF_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(),
      context.getClass(),
    ]);

    if (skipCsrf || isPublic) {
      return true;
    }

    // Skip validation for safe HTTP methods
    const method = request.method;
    if (['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      return true;
    }

    // Validate CSRF token for state-changing operations
    const user = request['user'] as { id: string } | undefined;
    if (!user) {
      throw new ForbiddenException('User not authenticated');
    }

    const csrfToken = request.headers['x-csrf-token'] as string;
    if (!csrfToken) {
      this.logger.warn(`Missing CSRF token for user ${user.id}`);
      throw new ForbiddenException('Missing CSRF token');
    }

    const isValid = await this.csrfService.validateCsrfToken(
      user.id,
      csrfToken,
    );
    if (!isValid) {
      throw new ForbiddenException('Invalid CSRF token');
    }

    return true;
  }
}
