import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { User } from '../../user/user.entity';

interface RequestWithUser {
  user: User;
}

/**
 * Decorator to extract the current authenticated admin user from the request
 * @example
 * @Get()
 * async getData(@CurrentAdmin() admin: User) {
 *   console.log(admin.id, admin.username, admin.role);
 * }
 */
export const CurrentAdmin = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext) => {
    const request = ctx.switchToHttp().getRequest<RequestWithUser>();
    return request.user;
  },
);
