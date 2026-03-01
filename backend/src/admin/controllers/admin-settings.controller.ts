import { Controller, Get, Post, Patch, Body, UseGuards } from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { CurrentAdmin } from '../decorators/current-admin.decorator';
import { AuditService } from '../services/audit.service';
import { AdminSettingsService } from '../services/settings.service';
import type { User } from '../../user/user.service';
import { UserRole } from '../../user/user.service';
import { PermissionAction, PermissionResource } from '@prisma/client';
import {
  SystemSettingsDto,
  MaintenanceModeDto,
  EmailSettingsDto,
  RateLimitSettingsDto,
  UploadSettingsDto,
  FeatureToggleDto,
  AllSettingsDto,
} from '../dto/settings.dto';

@Controller('admin/settings')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
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

  @Get('all')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async getAllSettings() {
    return this.settingsService.getAllSettings();
  }

  @Get('email')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async getEmailSettings() {
    return this.settingsService.getEmailSettings();
  }

  @Get('rate-limits')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async getRateLimitSettings() {
    return this.settingsService.getRateLimitSettings();
  }

  @Get('uploads')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async getUploadSettings() {
    return this.settingsService.getUploadSettings();
  }

  @Get('features')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async getFeatureToggles() {
    return this.settingsService.getFeatureToggles();
  }

  @Patch()
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async updateSettings(
    @Body() settingsDto: AllSettingsDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldSettings = await this.settingsService.getAllSettings();
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

  @Patch('email')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async updateEmailSettings(
    @Body() emailDto: EmailSettingsDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldSettings = await this.settingsService.getEmailSettings();
    const updatedSettings = await this.settingsService.updateSettings(emailDto);

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_EMAIL_SETTINGS',
      entityType: 'SYSTEM',
      entityId: 'system',
      oldValues: oldSettings,
      newValues: emailDto,
    });

    return {
      message: 'Email settings updated successfully',
      settings: updatedSettings,
    };
  }

  @Patch('rate-limits')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async updateRateLimitSettings(
    @Body() rateLimitDto: RateLimitSettingsDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldSettings = await this.settingsService.getRateLimitSettings();
    const updatedSettings =
      await this.settingsService.updateSettings(rateLimitDto);

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_RATE_LIMIT_SETTINGS',
      entityType: 'SYSTEM',
      entityId: 'system',
      oldValues: oldSettings,
      newValues: rateLimitDto,
    });

    return {
      message: 'Rate limit settings updated successfully',
      settings: updatedSettings,
    };
  }

  @Patch('uploads')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async updateUploadSettings(
    @Body() uploadDto: UploadSettingsDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldSettings = await this.settingsService.getUploadSettings();
    const updatedSettings =
      await this.settingsService.updateSettings(uploadDto);

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_UPLOAD_SETTINGS',
      entityType: 'SYSTEM',
      entityId: 'system',
      oldValues: oldSettings,
      newValues: uploadDto,
    });

    return {
      message: 'Upload settings updated successfully',
      settings: updatedSettings,
    };
  }

  @Patch('features')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async updateFeatureToggles(
    @Body() featureDto: FeatureToggleDto,
    @CurrentAdmin() admin: User,
  ) {
    const oldSettings = await this.settingsService.getFeatureToggles();
    const updatedSettings =
      await this.settingsService.updateSettings(featureDto);

    await this.auditService.log({
      performerId: admin.id,
      action: 'UPDATE_FEATURE_TOGGLES',
      entityType: 'SYSTEM',
      entityId: 'system',
      oldValues: oldSettings,
      newValues: featureDto,
    });

    return {
      message: 'Feature toggles updated successfully',
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

  @Post('reset')
  @RequireRoles(UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.UPDATE,
    resource: PermissionResource.SYSTEM,
  })
  async resetToDefaults(@CurrentAdmin() admin: User) {
    const oldSettings = await this.settingsService.getAllSettings();
    const defaultSettings = await this.settingsService.resetToDefaults();

    await this.auditService.log({
      performerId: admin.id,
      action: 'RESET_SETTINGS_TO_DEFAULTS',
      entityType: 'SYSTEM',
      entityId: 'system',
      oldValues: oldSettings,
      newValues: defaultSettings,
    });

    return {
      message: 'Settings reset to defaults successfully',
      settings: defaultSettings,
    };
  }
}
