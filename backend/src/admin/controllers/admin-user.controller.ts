import {
  Controller,
  Get,
  Post,
  Patch,
  Delete,
  Param,
  Body,
  Query,
  UseGuards,
  ConflictException,
} from '@nestjs/common';
import { randomUUID } from 'crypto';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { PermissionService } from '../services/permission.service';
import { AuditService } from '../services/audit.service';
import { UserService, UserRole } from '../../user/user.service';
import type { User } from '../../user/user.service';
import { PermissionAction, PermissionResource, Prisma } from '@prisma/client';
import {
  CreateUserDto,
  UpdateUserDto,
  BanUserDto,
  GrantPermissionDto,
  UserQueryDto,
  BulkActionDto,
  ResetPasswordDto,
} from '../dto/user-management.dto';
import * as bcrypt from 'bcrypt';

@Controller('admin/users')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminUserController {
  constructor(
    private userService: UserService,
    private permissionService: PermissionService,
    private auditService: AuditService,
  ) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.USER,
  })
  async findAll(@Query() query: UserQueryDto) {
    const { role, is_active, is_banned, page = 1, limit = 20 } = query;

    // Build Prisma where clause
    const baseWhere: Prisma.UserWhereInput = {};

    if (role) {
      baseWhere.role = role;
    }

    if (is_active !== undefined) {
      baseWhere.is_active = is_active;
    }

    if (is_banned !== undefined) {
      baseWhere.is_banned = is_banned;
    }

    // Search in username, email, or name with sanitized input
    // MySQL's LIKE is case-insensitive by default for strings
    const sanitizedSearch = query.getSanitizedSearch();
    if (sanitizedSearch) {
      // Prisma supports OR conditions with an array
      baseWhere.OR = [
        { username: { contains: sanitizedSearch } },
        { email: { contains: sanitizedSearch } },
        { name: { contains: sanitizedSearch } },
      ];
    }

    const [users, total] = await Promise.all([
      this.userService.findAll(baseWhere, { page, limit }),
      this.userService.count(baseWhere),
    ]);

    return {
      data: users,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  @Get(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.USER,
  })
  async findOne(@Param('id') id: string) {
    const user = await this.userService.findOne(id);
    if (!user) {
      return null;
    }

    // Get user permissions
    const permissions = await this.permissionService.getUserPermissions(id);

    // Get user stats
    const stats = await this.userService.getUserStats(id);

    return {
      ...user,
      permissions,
      stats,
    };
  }

  @Post()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.CREATE,
    resource: PermissionResource.USER,
  })
  async create(
    @Body() createUserDto: CreateUserDto,
    @CurrentAdmin() admin: User,
  ) {
    // Generate a unique ID using UUID
    const id = randomUUID();

    try {
      const user = await this.userService.create({
        ...createUserDto,
        id,
        role: createUserDto.role || UserRole.USER,
        is_active: createUserDto.is_active ?? true,
        is_banned: false,
      });

      await this.auditService.log({
        performerId: admin.id,
        action: 'CREATE_USER',
        entityType: 'USER',
        entityId: user.id,
        newValues: { ...createUserDto, password: '***' },
      });

      return user;
    } catch (error) {
      // Handle duplicate entry errors (Prisma unique constraint violations)
      if (
        error &&
        typeof error === 'object' &&
        'code' in error &&
        error.code === 'P2002'
      ) {
        // Prisma unique constraint violation
        const meta = error.meta;
        if (typeof meta === 'string') {
          if (
            meta.includes('username') ||
            meta.includes(createUserDto.username)
          ) {
            throw new ConflictException(
              `Username '${createUserDto.username}' already exists`,
            );
          }
          if (meta.includes('email') || meta.includes(createUserDto.email)) {
            throw new ConflictException(
              `Email '${createUserDto.email}' already exists`,
            );
          }
        }
        // Generic duplicate error
        throw new ConflictException(
          'A user with this username or email already exists',
        );
      }
      // Re-throw other errors
      throw error;
    }
  }

  @Patch(':id')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.USER,
  })
  async update(
    @Param('id') id: string,
    @Body() updateUserDto: UpdateUserDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldUser = await this.userService.findOne(id);
    const user = await this.userService.update(id, updateUserDto);

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_USER',
      entityType: 'USER',
      entityId: id,
      userId: id,
      oldValues: { ...oldUser, password: '***' },
      newValues: { ...updateUserDto, password: '***' },
    });

    return user;
  }

  @Delete(':id')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.USER,
  })
  async remove(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const oldUser = await this.userService.findOne(id);
    await this.userService.remove(id);

    await this.auditService.log({
      performerId: admin.id,
      action: 'DELETE_USER',
      entityType: 'USER',
      entityId: id,
      userId: id,
      oldValues: oldUser,
    });

    return { message: 'User deleted successfully' };
  }

  @Post(':id/ban')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.USER,
  })
  async banUser(
    @Param('id') id: string,
    @Body() banDto: BanUserDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldUser = await this.userService.findOne(id);
    const user = await this.userService.update(id, {
      is_banned: true,
      banned_until: banDto.until ? new Date(banDto.until) : undefined,
      banned_reason: banDto.reason,
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'BAN_USER',
      entityType: 'USER',
      entityId: id,
      userId: id,
      oldValues: { is_banned: oldUser?.is_banned },
      newValues: {
        is_banned: true,
        banned_until: banDto.until,
        banned_reason: banDto.reason,
      },
    });

    return user;
  }

  @Post(':id/unban')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.USER,
  })
  async unbanUser(@Param('id') id: string, @CurrentAdmin() admin: User) {
    const oldUser = await this.userService.findOne(id);
    const user = await this.userService.update(id, {
      is_banned: false,
      banned_until: undefined,
      banned_reason: undefined,
    });

    await this.auditService.log({
      performerId: admin.id,
      action: 'UNBAN_USER',
      entityType: 'USER',
      entityId: id,
      userId: id,
      oldValues: { is_banned: oldUser?.is_banned },
      newValues: { is_banned: false },
    });

    return user;
  }

  @Post(':id/permissions')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MANAGE_PERMISSIONS,
    resource: PermissionResource.USER,
  })
  async grantPermission(
    @Param('id') id: string,
    @Body() grantDto: GrantPermissionDto,
    @CurrentAdmin() admin: User,
  ) {
    await this.permissionService.grantPermission(
      id,
      grantDto.action,
      grantDto.resource,
      admin.id,
      grantDto.expires_at ? new Date(grantDto.expires_at) : undefined,
    );

    await this.auditService.log({
      performerId: admin.id,
      action: 'GRANT_PERMISSION',
      entityType: 'USER',
      entityId: id,
      userId: id,
      newValues: grantDto,
    });

    return { message: 'Permission granted successfully' };
  }

  @Delete(':id/permissions')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MANAGE_PERMISSIONS,
    resource: PermissionResource.USER,
  })
  async revokePermission(
    @Param('id') id: string,
    @Body('action') action: PermissionAction,
    @Body('resource') resource: PermissionResource,
    @CurrentAdmin() admin: User,
  ) {
    await this.permissionService.revokePermission(id, action, resource);

    await this.auditService.log({
      performerId: admin.id,
      action: 'REVOKE_PERMISSION',
      entityType: 'USER',
      entityId: id,
      userId: id,
      oldValues: { action, resource },
    });

    return { message: 'Permission revoked successfully' };
  }

  @Post('bulk-ban')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.USER,
  })
  async bulkBan(@Body() bulkDto: BulkActionDto, @CurrentAdmin() admin: User) {
    const results = await Promise.all(
      bulkDto.ids.map(async (id) => {
        try {
          await this.userService.update(id, {
            is_banned: true,
            banned_reason: bulkDto.reason,
          });
          return { id, success: true };
        } catch (error) {
          return { id, success: false, error: (error as Error).message };
        }
      }),
    );

    await this.auditService.log({
      performerId: admin.id,
      action: 'BULK_BAN_USERS',
      entityType: 'USER',
      newValues: { ids: bulkDto.ids, reason: bulkDto.reason },
    });

    return { results };
  }

  @Post('bulk-unban')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.MODERATE,
    resource: PermissionResource.USER,
  })
  async bulkUnban(@Body('ids') ids: string[], @CurrentAdmin() admin: User) {
    const results = await Promise.all(
      ids.map(async (id) => {
        try {
          await this.userService.update(id, {
            is_banned: false,
            banned_until: undefined,
            banned_reason: undefined,
          });
          return { id, success: true };
        } catch (error) {
          return { id, success: false, error: (error as Error).message };
        }
      }),
    );

    await this.auditService.log({
      performerId: admin.id,
      action: 'BULK_UNBAN_USERS',
      entityType: 'USER',
      newValues: { ids },
    });

    return { results };
  }

  @Delete('bulk-delete')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.DELETE,
    resource: PermissionResource.USER,
  })
  async bulkDelete(@Body('ids') ids: string[], @CurrentAdmin() admin: User) {
    const results = await Promise.all(
      ids.map(async (id) => {
        try {
          await this.userService.remove(id);
          return { id, success: true };
        } catch (error) {
          return { id, success: false, error: (error as Error).message };
        }
      }),
    );

    await this.auditService.log({
      performerId: admin.id,
      action: 'BULK_DELETE_USERS',
      entityType: 'USER',
      newValues: { ids },
    });

    return { results };
  }

  @Post(':id/reset-password')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.USER,
  })
  async resetPassword(
    @Param('id') id: string,
    @Body() resetDto: ResetPasswordDto,
    @CurrentAdmin() admin: User,
  ) {
    const hashedPassword = await bcrypt.hash(resetDto.password, 10);
    await this.userService.update(id, { password: hashedPassword });

    await this.auditService.log({
      performerId: admin.id,
      action: 'RESET_USER_PASSWORD',
      entityType: 'USER',
      entityId: id,
      userId: id,
    });

    return { message: 'Password reset successfully' };
  }
}
