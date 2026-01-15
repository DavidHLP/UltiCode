import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { AdminDashboardService } from '../services/admin-dashboard.service';
import { UserRole } from '../../user/user.entity';
import { PermissionAction, PermissionResource } from '@prisma/client';
import { ChartQueryDto } from '../dto/dashboard.dto';

@Controller('admin/dashboard')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminDashboardController {
  constructor(private dashboardService: AdminDashboardService) {}

  @Get('stats')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async getStats() {
    return this.dashboardService.getDashboardStats();
  }

  @Get('charts')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async getChartStats(@Query() query: ChartQueryDto) {
    const { period, metric, days, startDate, endDate } = query;

    if (period === undefined || metric === undefined || days === undefined) {
      throw new Error('Missing required query parameters');
    }

    return this.dashboardService.getChartStats({
      period,
      metric,
      days,
      startDate: startDate ? new Date(startDate) : undefined,
      endDate: endDate ? new Date(endDate) : undefined,
    });
  }
}
