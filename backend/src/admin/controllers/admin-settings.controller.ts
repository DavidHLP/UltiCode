import { Controller, Get, Post, Patch, Body, UseGuards } from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { AuditService } from '../services/audit.service';
import { AdminSettingsService } from '../services/settings.service';
import { UserRole, User } from '../../user/user.entity';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { SystemSettingsDto, MaintenanceModeDto } from '../dto/settings.dto';

@Controller('admin/settings')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard)
export class AdminSettingsController {
  constructor(
    private settingsService: AdminSettingsService,
    private auditService: AuditService,
  ) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async getSettings() {
    return this.settingsService.getSettings();
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
    const oldSettings = await this.settingsService.getSettings();
    const updatedSettings =
      await this.settingsService.updateSettings(settingsDto);

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_SETTINGS',
      entityType: 'SYSTEM',
      entityId: 'system',
      oldValues: oldSettings,
      newValues: settingsDto,
    });

    return {
      message: 'Settings updated successfully',
      settings: updatedSettings,
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
    const updateDto = new SystemSettingsDto();
    updateDto.maintenance_mode = maintenanceDto.enabled;
    if (maintenanceDto.message) {
      updateDto.maintenance_message = maintenanceDto.message;
    }

    const updatedSettings =
      await this.settingsService.updateSettings(updateDto);

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
      maintenance_mode: updatedSettings.maintenance_mode,
    };
  }

  @Post('cache/clear')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async clearCache(@CurrentAdmin() admin: User) {
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
