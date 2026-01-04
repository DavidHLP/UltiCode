import { SetMetadata } from '@nestjs/common';
import { UserRole } from '@prisma/client';

export const ROLES_KEY = 'roles';

/**
 * Decorator to specify required roles for a route
 * @example
 * @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
 */
export const RequireRoles = (...roles: UserRole[]) =>
  SetMetadata(ROLES_KEY, roles);
