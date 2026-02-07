import type {
  PrismaClient,
  UserRole,
  PermissionAction,
  PermissionResource,
} from '@prisma/client';
import type {
  SeederMetadata,
  SeedModuleResult,
  TransactionClient,
} from '../../core/interfaces';
import { BaseSeeder, createSeederExport } from '../base/base.seeder';
import { PasswordHasher } from '../../utils/password-hasher';

/**
 * Permissions seeder - creates role permissions and admin users.
 *
 * Layer: L5 (depends on Users)
 *
 * This seeder uses upsert for idempotency, which is important
 * for permissions that may need to be updated without clearing.
 */
export class PermissionsSeeder extends BaseSeeder {
  readonly metadata: SeederMetadata = {
    name: 'Permissions',
    version: '1.0.0',
    dependencies: ['Users'],
    priority: 10,
    description: 'Seed role permissions and admin users',
  };

  async clear(tx?: TransactionClient): Promise<void> {
    const client = this.getClient(tx) as PrismaClient;
    await client.userPermission.deleteMany({});
    await client.rolePermission.deleteMany({});
  }

  async seed(tx?: TransactionClient): Promise<SeedModuleResult> {
    const startTime = Date.now();
    const client = this.getClient(tx) as PrismaClient;
    const details: Record<string, number> = {};

    // Build permissions array
    const permissions: Array<{
      role: UserRole;
      action: PermissionAction;
      resource: PermissionResource;
    }> = [];

    // SUPER_ADMIN - Full access
    const superAdminResources: PermissionResource[] = [
      'USER',
      'PROBLEM',
      'CONTEST',
      'SOLUTION',
      'FORUM_POST',
      'FORUM_COMMENT',
      'SYSTEM',
      'PROBLEM_LIST',
    ];
    const superAdminActions: PermissionAction[] = [
      'CREATE',
      'READ',
      'UPDATE',
      'DELETE',
      'MODERATE',
      'PUBLISH',
      'MANAGE_USERS',
      'MANAGE_PERMISSIONS',
    ];
    for (const resource of superAdminResources) {
      for (const action of superAdminActions) {
        permissions.push({ role: 'SUPER_ADMIN', action, resource });
      }
    }

    // ADMIN - Full access except MANAGE_PERMISSIONS
    const adminResources: PermissionResource[] = [
      'USER',
      'PROBLEM',
      'CONTEST',
      'SOLUTION',
      'FORUM_POST',
      'FORUM_COMMENT',
      'SYSTEM',
      'PROBLEM_LIST',
    ];
    const adminActions: PermissionAction[] = [
      'CREATE',
      'READ',
      'UPDATE',
      'DELETE',
      'MODERATE',
      'PUBLISH',
      'MANAGE_USERS',
    ];
    for (const resource of adminResources) {
      for (const action of adminActions) {
        permissions.push({ role: 'ADMIN', action, resource });
      }
    }

    // MODERATOR - Read all, moderate content
    const moderatorPermissions: Array<{
      action: PermissionAction;
      resource: PermissionResource;
    }> = [
      { action: 'READ', resource: 'USER' },
      { action: 'READ', resource: 'PROBLEM' },
      { action: 'READ', resource: 'CONTEST' },
      { action: 'READ', resource: 'SOLUTION' },
      { action: 'READ', resource: 'FORUM_POST' },
      { action: 'READ', resource: 'FORUM_COMMENT' },
      { action: 'READ', resource: 'SYSTEM' },
      { action: 'MODERATE', resource: 'SOLUTION' },
      { action: 'MODERATE', resource: 'FORUM_POST' },
      { action: 'MODERATE', resource: 'FORUM_COMMENT' },
      { action: 'UPDATE', resource: 'SOLUTION' },
      { action: 'DELETE', resource: 'SOLUTION' },
      { action: 'UPDATE', resource: 'FORUM_POST' },
      { action: 'DELETE', resource: 'FORUM_POST' },
      { action: 'UPDATE', resource: 'FORUM_COMMENT' },
      { action: 'DELETE', resource: 'FORUM_COMMENT' },
    ];
    for (const perm of moderatorPermissions) {
      permissions.push({ role: 'MODERATOR', ...perm });
    }

    // Insert permissions using upsert for idempotency
    let permissionCount = 0;
    for (const perm of permissions) {
      await client.rolePermission.upsert({
        where: {
          role_action_resource: {
            role: perm.role,
            action: perm.action,
            resource: perm.resource,
          },
        },
        create: perm,
        update: perm,
      });
      permissionCount++;
    }
    details.rolePermissions = permissionCount;

    // Create admin users
    const hasher = new PasswordHasher(this.context.environment);

    // Super admin
    const adminPassword = await hasher.hash('admin123');
    await client.user.upsert({
      where: { username: 'admin' },
      create: {
        id: 'u-admin-001',
        username: 'admin',
        email: 'admin@ulticode.com',
        name: 'System Administrator',
        password: adminPassword,
        role: 'SUPER_ADMIN',
        is_active: true,
        is_banned: false,
        avatar: 'https://api.dicebear.com/7.x/shapes/svg?seed=admin',
        joined_at: new Date(),
      },
      update: {
        role: 'SUPER_ADMIN',
        is_active: true,
        is_banned: false,
      },
    });

    // Moderator
    const modPassword = await hasher.hash('mod123');
    await client.user.upsert({
      where: { username: 'moderator' },
      create: {
        id: 'u-mod-001',
        username: 'moderator',
        email: 'moderator@ulticode.com',
        name: 'Content Moderator',
        password: modPassword,
        role: 'MODERATOR',
        is_active: true,
        is_banned: false,
        avatar: 'https://api.dicebear.com/7.x/shapes/svg?seed=moderator',
        joined_at: new Date(),
      },
      update: {
        role: 'MODERATOR',
        is_active: true,
        is_banned: false,
      },
    });
    details.adminUsers = 2;

    const totalCount = Object.values(details).reduce((sum, n) => sum + n, 0);
    return this.createResult(totalCount, startTime, details);
  }
}

export const createPermissionsSeeder = createSeederExport(PermissionsSeeder);
