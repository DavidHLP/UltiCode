import { Controller, Post, Body, UseGuards } from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { AuditService } from '../services/audit.service';
import { PrismaService } from '../../prisma.service';
import { UserRole, User } from '../../user/user.entity';
import { PermissionAction, PermissionResource, Prisma } from '@prisma/client';
import {
  BulkBanDto,
  BulkRoleDto,
  BulkProblemActionDto,
  BulkSolutionActionDto,
  BulkForumActionDto,
} from '../dto/settings.dto';

@Controller('admin/bulk')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminBulkController {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
  ) {}

  @Post('users/ban')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.USER,
  })
  async bulkBanUsers(@Body() bulkDto: BulkBanDto, @CurrentAdmin() admin: User) {
    const { ids, reason, until } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        await this.prisma.user.update({
          where: { id },
          data: {
            is_banned: true,
            banned_until: until ? new Date(until) : undefined,
            banned_reason: reason,
          },
        });
        results.push({ id, success: true });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to ban user' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: 'BULK_BAN_USERS',
      entityType: 'USER',
      entityId: ids.join(','),
      newValues: { reason, count: ids.length },
    });

    return { results };
  }

  @Post('users/unban')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.USER,
  })
  async bulkUnbanUsers(
    @Body() bulkDto: { ids: string[] },
    @CurrentAdmin() admin: User,
  ) {
    const { ids } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        await this.prisma.user.update({
          where: { id },
          data: {
            is_banned: false,
            banned_until: null,
            banned_reason: null,
          },
        });
        results.push({ id, success: true });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to unban user' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: 'BULK_UNBAN_USERS',
      entityType: 'USER',
      entityId: ids.join(','),
      newValues: { count: ids.length },
    });

    return { results };
  }

  @Post('users/role')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.USER,
  })
  async bulkChangeRole(
    @Body() bulkDto: BulkRoleDto,
    @CurrentAdmin() admin: User,
  ) {
    const { ids, role } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        await this.prisma.user.update({
          where: { id },
          data: { role },
        });
        results.push({ id, success: true });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to change role' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: 'BULK_CHANGE_ROLE',
      entityType: 'USER',
      entityId: ids.join(','),
      newValues: { role, count: ids.length },
    });

    return { results };
  }

  @Post('users/delete')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.USER,
  })
  async bulkDeleteUsers(
    @Body() bulkDto: { ids: string[] },
    @CurrentAdmin() admin: User,
  ) {
    const { ids } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        await this.prisma.user.delete({
          where: { id },
        });
        results.push({ id, success: true });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to delete user' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: 'BULK_DELETE_USERS',
      entityType: 'USER',
      entityId: ids.join(','),
      newValues: { count: ids.length },
    });

    return { results };
  }

  @Post('problems/publish')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.PUBLISH,
    resource: PermissionResource.PROBLEM,
  })
  async bulkPublishProblems(
    @Body() bulkDto: BulkProblemActionDto,
    @CurrentAdmin() admin: User,
  ) {
    const { ids, action } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        const updateData: Prisma.ProblemUpdateInput = {};
        switch (action) {
          case 'publish':
            updateData.is_published = true;
            updateData.published_at = new Date();
            updateData.published_by = admin.id;
            break;
          case 'unpublish':
            updateData.is_published = false;
            break;
          case 'delete':
            updateData.is_deleted = true;
            updateData.deleted_at = new Date();
            updateData.deleted_by = admin.id;
            break;
          case 'restore':
            updateData.is_deleted = false;
            updateData.deleted_at = null;
            updateData.deleted_by = null;
            break;
        }
        await this.prisma.problem.update({
          where: { id: BigInt(id) },
          data: updateData,
        });
        results.push({ id, success: true });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to perform action' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: `BULK_${action.toUpperCase()}_PROBLEMS`,
      entityType: 'PROBLEM',
      entityId: ids.join(','),
      newValues: { action, count: ids.length },
    });

    return { results };
  }

  @Post('solutions/approve')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.SOLUTION,
  })
  async bulkApproveSolutions(
    @Body() bulkDto: BulkSolutionActionDto,
    @CurrentAdmin() admin: User,
  ) {
    const { ids, action } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        const updateData: Prisma.SolutionUpdateInput = {};
        switch (action) {
          case 'delete':
            updateData.is_deleted = true;
            updateData.deleted_at = new Date();
            updateData.deleted_by = admin.id;
            break;
          case 'unflag':
            updateData.is_flagged = false;
            updateData.flagged_at = null;
            updateData.flagged_reason = null;
            break;
          case 'publish':
            updateData.is_published = true;
            updateData.published_at = new Date();
            updateData.published_by = admin.id;
            break;
          case 'unpublish':
            updateData.is_published = false;
            break;
        }
        await this.prisma.solution.update({
          where: { id: id },
          data: updateData,
        });
        results.push({ id, success: true });
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to perform action' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: `BULK_${action.toUpperCase()}_SOLUTIONS`,
      entityType: 'SOLUTION',
      entityId: ids.join(','),
      newValues: { action, count: ids.length },
    });

    return { results };
  }

  @Post('forum/delete')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN, UserRole.MODERATOR)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.FORUM_POST,
  })
  async bulkDeleteForumContent(
    @Body() bulkDto: BulkForumActionDto,
    @CurrentAdmin() admin: User,
  ) {
    const { ids, action } = bulkDto;
    const results: { id: string; success: boolean; error?: string }[] = [];

    for (const id of ids) {
      try {
        if (action === 'delete') {
          // Try as post
          try {
            await this.prisma.forumPost.update({
              where: { id: id },
              data: {
                is_deleted: true,
                deleted_at: new Date(),
                deleted_by: admin.id,
              },
            });
            results.push({ id, success: true });
            continue;
          } catch {
            // Try as comment
            try {
              // ForumComment has no is_deleted field, so we delete it
              await this.prisma.forumComment.delete({
                where: { id: id },
              });
              results.push({ id, success: true });
              continue;
            } catch {
              results.push({ id, success: false, error: 'Not found' });
            }
          }
        } else if (action === 'unflag') {
          // Try as post
          try {
            await this.prisma.forumPost.update({
              where: { id: id },
              data: {
                is_flagged: false,
                flagged_at: null,
                flagged_reason: null,
              },
            });
            results.push({ id, success: true });
            continue;
          } catch {
            // Try as comment
            results.push({
              id,
              success: false,
              error: 'Not applicable to comments',
            });
          }
        }
      } catch (_error) {
        results.push({ id, success: false, error: 'Failed to perform action' });
      }
    }

    await this.auditService.log({
      performerId: admin.id,
      action: `BULK_${action.toUpperCase()}_FORUM`,
      entityType: 'FORUM',
      entityId: ids.join(','),
      newValues: { action, count: ids.length },
    });

    return { results };
  }
}
