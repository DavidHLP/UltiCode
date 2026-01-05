import {
  PrismaClient,
  UserRole,
  PermissionAction,
  PermissionResource,
} from '@prisma/client';
import * as bcrypt from 'bcrypt';

/**
 * Clear all permissions
 */
export async function clearPermissions(prisma: PrismaClient): Promise<void> {
  await prisma.userPermission.deleteMany({});
  await prisma.rolePermission.deleteMany({});
}

/**
 * Seed default role permissions
 */
export async function seedRolePermissions(
  prisma: PrismaClient,
): Promise<{ count: number }> {
  const permissions: Array<{
    role: UserRole;
    action: PermissionAction;
    resource: PermissionResource;
  }> = [];

  // SUPER_ADMIN - Full access to everything
  const superAdminResources: PermissionResource[] = [
    'USER',
    'PROBLEM',
    'CONTEST',
    'SOLUTION',
    'FORUM_POST',
    'FORUM_COMMENT',
    'SYSTEM',
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

  // MODERATOR - Read all, moderate content, no user management
  const moderatorPermissions: Array<{
    action: PermissionAction;
    resource: PermissionResource;
  }> = [
    // Read access to all
    { action: 'READ', resource: 'USER' },
    { action: 'READ', resource: 'PROBLEM' },
    { action: 'READ', resource: 'CONTEST' },
    { action: 'READ', resource: 'SOLUTION' },
    { action: 'READ', resource: 'FORUM_POST' },
    { action: 'READ', resource: 'FORUM_COMMENT' },
    { action: 'READ', resource: 'SYSTEM' },
    // Moderate content
    { action: 'MODERATE', resource: 'SOLUTION' },
    { action: 'MODERATE', resource: 'FORUM_POST' },
    { action: 'MODERATE', resource: 'FORUM_COMMENT' },
    // Update and delete moderated content
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

  // USER - No admin permissions (only normal user operations handled by public endpoints)
  // No permissions needed for USER role in admin system

  // Insert all permissions
  let count = 0;
  for (const perm of permissions) {
    await prisma.rolePermission.upsert({
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
    count++;
  }

  return { count };
}

/**
 * Seed a default super admin user
 */
export async function seedAdminUsers(
  prisma: PrismaClient,
): Promise<{ count: number }> {
  // Hash password: "admin123"
  const hashedPassword = await bcrypt.hash('admin123', 10);

  await prisma.user.upsert({
    where: { username: 'admin' },
    create: {
      id: 'u-admin-001',
      username: 'admin',
      email: 'admin@ulticode.com',
      name: 'System Administrator',
      password: hashedPassword,
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

  // Create a moderator user: "moderator" / "mod123"
  const modPassword = await bcrypt.hash('mod123', 10);

  await prisma.user.upsert({
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

  return { count: 2 };
}
