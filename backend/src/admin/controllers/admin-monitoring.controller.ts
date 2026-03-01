import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import {
  ApiTags,
  ApiOperation,
  ApiResponse,
  ApiBearerAuth,
  ApiQuery,
} from '@nestjs/swagger';
import { AuthGuard } from '../../auth/auth.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { SandboxMonitoringService } from '../../submission/sandbox/sandbox-monitoring.service';
import { ExecutionStatus } from '@prisma/client';

@ApiTags('admin/monitoring')
@Controller('admin/monitoring')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
@ApiBearerAuth()
export class AdminMonitoringController {
  constructor(private readonly monitoringService: SandboxMonitoringService) {}

  /**
   * Get sandbox metrics summary
   */
  @Get('metrics')
  @ApiOperation({
    summary: 'Get sandbox metrics',
    description: 'Get aggregated sandbox performance metrics',
  })
  @ApiResponse({ status: 200, description: 'Sandbox metrics summary' })
  @ApiQuery({
    name: 'startDate',
    required: false,
    description: 'Start date for metrics (ISO string)',
  })
  @ApiQuery({
    name: 'endDate',
    required: false,
    description: 'End date for metrics (ISO string)',
  })
  async getMetrics(
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    const start = startDate
      ? new Date(startDate)
      : new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const end = endDate ? new Date(endDate) : new Date();

    return this.monitoringService.getMetricsSummary(start, end);
  }

  /**
   * Get language-specific metrics
   */
  @Get('languages')
  @ApiOperation({
    summary: 'Get language metrics',
    description: 'Get performance metrics grouped by programming language',
  })
  @ApiResponse({ status: 200, description: 'Language-specific metrics' })
  async getLanguageMetrics() {
    return this.monitoringService.getLanguageMetrics();
  }

  /**
   * Get execution logs
   */
  @Get('logs')
  @ApiOperation({
    summary: 'Get execution logs',
    description: 'Get sandbox execution logs with filtering',
  })
  @ApiResponse({ status: 200, description: 'Paginated execution logs' })
  async getLogs(
    @Query('language') language?: string,
    @Query('status') status?: ExecutionStatus,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
  ) {
    return this.monitoringService.getExecutionLogs({
      language,
      status,
      startDate: startDate ? new Date(startDate) : undefined,
      endDate: endDate ? new Date(endDate) : undefined,
      limit: limit ? parseInt(limit, 10) : 50,
      offset: offset ? parseInt(offset, 10) : 0,
    });
  }

  /**
   * Cleanup old logs
   */
  @Get('cleanup')
  async cleanupLogs(@Query('days') days?: string) {
    const olderThanDays = days ? parseInt(days, 10) : 30;
    const count = await this.monitoringService.cleanupOldLogs(olderThanDays);
    return { message: `Cleaned up ${count} execution logs`, count };
  }
}
