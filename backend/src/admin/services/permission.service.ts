import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import {
  PermissionAction,
  PermissionResource,
  User,
  UserRole,
} from '@prisma/client';

@Injectable()
export class PermissionService {
  private rolePermissionCache = new Map<
    string,
    Array<{
      action: PermissionAction;
      resource: PermissionResource;
    }>
  >();
  private readonly CACHE_TTL = 5 * 60 * 1000; // 5 minutes

  constructor(private prisma: PrismaService) {}

  /**
   * Check if user has a specific permission
   * Checks both role permissions and direct user permissions
   */
  async hasPermission(
    userId: string,
    action: PermissionAction,
    resource: PermissionResource,
  ): Promise<boolean> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      include: { permissions: true },
    });

    if (!user) {
      return false;
    }

    // Check cached role permissions
    let rolePerms = this.rolePermissionCache.get(user.role);
    if (!rolePerms) {
      rolePerms = await this.prisma.rolePermission.findMany({
        where: { role: user.role },
        select: { action: true, resource: true },
      });
      this.rolePermissionCache.set(user.role, rolePerms);

      // Clear cache after TTL
      setTimeout(() => {
        this.rolePermissionCache.delete(user.role);
      }, this.CACHE_TTL);
    }

    const hasRolePerm = rolePerms.some(
      (p) => p.action === action && p.resource === resource,
    );

    if (hasRolePerm) {
      return true;
    }

    // Check direct user permissions
    const hasDirectPerm = user.permissions.some(
      (p) =>
        p.action === action &&
        p.resource === resource &&
        (!p.expires_at || p.expires_at > new Date()),
    );

    return hasDirectPerm;
  }

  /**
   * Check if user has any of the specified roles
   */
  hasRole(user: User, roles: UserRole[]): boolean {
    return roles.includes(user.role);
  }

  /**
   * Get all permissions for a user (role + direct)
   */
  async getUserPermissions(userId: string): Promise<
    Array<{
      action: PermissionAction;
      resource: PermissionResource;
      source: 'role' | 'direct';
      expiresAt: Date | null;
    }>
  > {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      include: { permissions: true },
    });

    if (!user) {
      return [];
    }

    // Get role permissions
    const rolePerms = await this.prisma.rolePermission.findMany({
      where: { role: user.role },
    });

    const rolePermissionsResult = rolePerms.map((p) => ({
      action: p.action,
      resource: p.resource,
      source: 'role' as 'role' | 'direct',
      expiresAt: null as Date | null,
    }));

    // Add direct permissions
    const directPermissionsResult: Array<{
      action: PermissionAction;
      resource: PermissionResource;
      source: 'role' | 'direct';
      expiresAt: Date | null;
    }> = [];

    for (const perm of user.permissions) {
      if (!perm.expires_at || perm.expires_at > new Date()) {
        directPermissionsResult.push({
          action: perm.action,
          resource: perm.resource,
          source: 'direct' as 'role' | 'direct',
          expiresAt: perm.expires_at || null,
        });
      }
    }

    return [...rolePermissionsResult, ...directPermissionsResult];
  }

  /**
   * Grant direct permission to a user
   */
  async grantPermission(
    userId: string,
    action: PermissionAction,
    resource: PermissionResource,
    grantedBy: string,
    expiresAt?: Date,
  ): Promise<void> {
    await this.prisma.userPermission.upsert({
      where: {
        user_id_action_resource: {
          user_id: userId,
          action,
          resource,
        },
      },
      create: {
        user_id: userId,
        action,
        resource,
        granted_by: grantedBy,
        expires_at: expiresAt,
      },
      update: {
        granted_by: grantedBy,
        expires_at: expiresAt,
      },
    });
  }

  /**
   * Revoke direct permission from a user
   */
  async revokePermission(
    userId: string,
    action: PermissionAction,
    resource: PermissionResource,
  ): Promise<void> {
    await this.prisma.userPermission.deleteMany({
      where: {
        user_id: userId,
        action,
        resource,
      },
    });
  }

  /**
   * Update user role
   */
  async updateUserRole(
    userId: string,
    role: UserRole,
    updatedBy: string,
  ): Promise<void> {
    await this.prisma.user.update({
      where: { id: userId },
      data: {
        role,
        updated_by: updatedBy,
      },
    });
  }

  /**
   * Clear cache when role permissions change
   */
  clearRoleCache(role: UserRole): void {
    this.rolePermissionCache.delete(role);
  }
}
