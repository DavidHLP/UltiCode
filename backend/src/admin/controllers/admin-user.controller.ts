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
} from '@nestjs/common';
import { Like, FindOptionsWhere } from 'typeorm';
import { AuthGuard } from '../../auth/auth.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { PermissionService } from '../services/permission.service';
import { AuditService } from '../services/audit.service';
import { UserService } from '../../user/user.service';
import { UserRole } from '../../user/user.entity';
import { PermissionAction, PermissionResource } from '@prisma/client';
import {
  CreateUserDto,
  UpdateUserDto,
  BanUserDto,
  GrantPermissionDto,
  UserQueryDto,
} from '../dto/user-management.dto';
import { User } from '../../user/user.entity';

@Controller('admin/users')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard)
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

    // Build query conditions using proper TypeORM syntax
    const baseWhere: FindOptionsWhere<User> = {};

    if (role) {
      baseWhere.role = role;
    }

    if (is_active !== undefined) {
      baseWhere.is_active = is_active;
    }

    if (is_banned !== undefined) {
      baseWhere.is_banned = is_banned;
    }

    let where: FindOptionsWhere<User> | FindOptionsWhere<User>[] = baseWhere;

    // Search in username, email, or name with sanitized input
    // Using TypeORM's Like operator which uses parameterized queries
    const sanitizedSearch = query.getSanitizedSearch();
    if (sanitizedSearch) {
      const searchPattern = `%${sanitizedSearch}%`;
      // TypeORM supports OR conditions via an array of objects.
      // We must distribute the base AND conditions into each OR branch.
      where = [
        { ...baseWhere, username: Like(searchPattern) },
        { ...baseWhere, email: Like(searchPattern) },
        { ...baseWhere, name: Like(searchPattern) },
      ];
    }

    const [users, total] = await Promise.all([
      this.userService.findAll(where, { page, limit }),
      this.userService.count(where),
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

    return {
      ...user,
      permissions,
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
    const user = await this.userService.create({
      ...createUserDto,
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
}
