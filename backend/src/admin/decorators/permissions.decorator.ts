import { SetMetadata } from '@nestjs/common';
import { RequiredPermission } from '../types/admin.types';

export const PERMISSIONS_KEY = 'permissions';

/**
 * Decorator to specify required permissions for a route
 * @example
 * @RequirePermissions(
 *   { action: 'READ', resource: 'USER' },
 *   { action: 'UPDATE', resource: 'USER' }
 * )
 */
export const RequirePermissions = (...permissions: RequiredPermission[]) =>
  SetMetadata(PERMISSIONS_KEY, permissions);
