import {
  Injectable,
  CanActivate,
  ExecutionContext,
  ForbiddenException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { PermissionService } from '../services/permission.service';
import { PERMISSIONS_KEY } from '../decorators/permissions.decorator';
import { User, UserRole } from '../../user/user.entity';
import { RequiredPermission } from '../types/admin.types';

@Injectable()
export class PermissionsGuard implements CanActivate {
  constructor(
    private reflector: Reflector,
    private permissionService: PermissionService,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const requiredPermissions =
      this.reflector.getAllAndOverride<RequiredPermission[]>(PERMISSIONS_KEY, [
        context.getHandler(),
        context.getClass(),
      ]) || [];

    if (!requiredPermissions || requiredPermissions.length === 0) {
      return true;
    }

    const request = context.switchToHttp().getRequest<{ user: User }>();
    const user = request.user;

    if (!user) {
      throw new ForbiddenException('User not authenticated');
    }

    // Super Admin has all permissions
    if (user.role === UserRole.SUPER_ADMIN) {
      return true;
    }

    for (const perm of requiredPermissions) {
      const hasPermission = await this.permissionService.hasPermission(
        user.id,
        perm.action,
        perm.resource,
      );

      if (!hasPermission) {
        throw new ForbiddenException(
          `Insufficient permissions: ${perm.action}:${perm.resource}`,
        );
      }
    }

    return true;
  }
}
