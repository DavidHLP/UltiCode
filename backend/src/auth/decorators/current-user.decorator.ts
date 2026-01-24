import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import type { Request } from 'express';
import { User } from '../../user/user.entity';

interface RequestWithUser extends Request {
  user?: User;
}

/**
 * Decorator to extract the current authenticated user from the request
 * @example
 * @Get()
 * async getData(@CurrentUser() user: User) {
 *   console.log(user.id, user.username, user.role);
 * }
 */
export const CurrentUser = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): User => {
    const request = ctx.switchToHttp().getRequest<RequestWithUser>();
    const user = request.user;

    if (!user) {
      throw new Error(
        'User not found in request - ensure AuthGuard is applied',
      );
    }

    return user;
  },
);
