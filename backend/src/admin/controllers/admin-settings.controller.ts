import { Controller, Get, Post, Patch, Body, UseGuards } from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { AuditService } from '../services/audit.service';
import { PrismaService } from '../../prisma.service';
import { UserRole, User } from '../../user/user.entity';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { SystemSettingsDto, MaintenanceModeDto } from '../dto/settings.dto';

@Controller('admin/settings')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard)
export class AdminSettingsController {
  constructor(
    private prisma: PrismaService,
    private auditService: AuditService,
  ) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  getSettings() {
    // In a real implementation, settings would be stored in a dedicated table
    // For now, return default settings
    return {
      maintenance_mode: false,
      maintenance_message:
        'Site is under maintenance. Please check back later.',
      enable_registrations: true,
      site_name: 'UltiCode',
      site_description: 'Competitive Programming Platform',
      require_email_verification: false,
    };
  }

  @Patch()
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async updateSettings(
    @Body() settingsDto: SystemSettingsDto,
    @CurrentAdmin() admin: User,
  ) {
    // In a real implementation, update settings in database
    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_SETTINGS',
      entityType: 'SYSTEM',
      entityId: 'system',
      oldValues: {},
      newValues: settingsDto,
    });

    return {
      message: 'Settings updated successfully',
      settings: settingsDto,
    };
  }

  @Post('maintenance')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async toggleMaintenance(
    @Body() maintenanceDto: MaintenanceModeDto,
    @CurrentAdmin() admin: User,
  ) {
    await this.auditService.log({
      performerId: admin.id,
      action: maintenanceDto.enabled
        ? 'ENABLE_MAINTENANCE'
        : 'DISABLE_MAINTENANCE',
      entityType: 'SYSTEM',
      entityId: 'system',
      newValues: maintenanceDto,
    });

    return {
      message: maintenanceDto.enabled
        ? 'Maintenance mode enabled'
        : 'Maintenance mode disabled',
      maintenance_mode: maintenanceDto.enabled,
    };
  }

  @Post('cache/clear')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async clearCache(@CurrentAdmin() admin: User) {
    // In a real implementation, clear Redis cache, etc.
    await this.auditService.log({
      performerId: admin.id,
      action: 'CLEAR_CACHE',
      entityType: 'SYSTEM',
      entityId: 'system',
      newValues: { timestamp: new Date() },
    });

    return { message: 'Cache cleared successfully' };
  }
}
