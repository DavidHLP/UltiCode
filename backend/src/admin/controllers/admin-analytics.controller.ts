import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { UserRole } from '../../user/user.service';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { AdminAnalyticsService } from '../services/admin-analytics.service';
import { AnalyticsQueryDto } from '../dto/analytics.dto';

@Controller('admin/analytics')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminAnalyticsController {
  constructor(private analyticsService: AdminAnalyticsService) {}

  @Get()
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async getReport(@Query() query: AnalyticsQueryDto) {
    return this.analyticsService.getReport(query);
  }

  @Get('user-activity')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.USER,
  })
  async getUserActivity(
    @Query('days') days?: number,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    return this.analyticsService.getReport({
      reportType: 'user_activity' as never,
      days: days || 30,
      startDate,
      endDate,
    } as AnalyticsQueryDto);
  }

  @Get('problem-completion')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.PROBLEM,
  })
  async getProblemCompletion(
    @Query('days') days?: number,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    return this.analyticsService.getReport({
      reportType: 'problem_completion' as never,
      days: days || 30,
      startDate,
      endDate,
    } as AnalyticsQueryDto);
  }

  @Get('contest-participation')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.CONTEST,
  })
  async getContestParticipation(
    @Query('days') days?: number,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    return this.analyticsService.getReport({
      reportType: 'contest_participation' as never,
      days: days || 30,
      startDate,
      endDate,
    } as AnalyticsQueryDto);
  }

  @Get('revenue')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async getRevenue(
    @Query('days') days?: number,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    return this.analyticsService.getReport({
      reportType: 'revenue' as never,
      days: days || 30,
      startDate,
      endDate,
    } as AnalyticsQueryDto);
  }

  @Get('performance')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async getPerformance() {
    return this.analyticsService.getReport({
      reportType: 'performance' as never,
    } as AnalyticsQueryDto);
  }
}
