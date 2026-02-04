import { Controller, Get, Query, UseGuards, Response } from '@nestjs/common';
import type { Response as ExpressResponse } from 'express';
import { AuthGuard } from '../../auth/auth.guard';
import { CsrfGuard } from '../../auth/csrf.guard';
import { PermissionsGuard } from '../guards/permissions.guard';
import { RolesGuard } from '../guards/roles.guard';
import { RequirePermissions } from '../decorators/permissions.decorator';
import { RequireRoles } from '../decorators/roles.decorator';
import { AuditService } from '../services/audit.service';
import { UserRole } from '../../user/user.service';
import { PermissionAction, PermissionResource } from '@prisma/client';
import {
  AuditLogQueryDto,
  AuditLogExportDto,
  AuditStatsDto,
} from '../dto/audit.dto';

@Controller('admin/audit')
@UseGuards(AuthGuard, PermissionsGuard, RolesGuard, CsrfGuard)
export class AdminAuditController {
  constructor(private auditService: AuditService) {}

  @Get('logs')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async getAuditLogs(@Query() query: AuditLogQueryDto) {
    const {
      performerId,
      userId,
      entityType,
      entityId,
      startDate,
      endDate,
      page = 1,
      limit = 50,
    } = query;

    const skip = (page - 1) * limit;

    const result = await this.auditService.getAuditLogs({
      skip,
      take: limit,
      performerId,
      userId,
      entityType,
      entityId,
      startDate: startDate ? new Date(startDate) : undefined,
      endDate: endDate ? new Date(endDate) : undefined,
    });

    return {
      ...result,
      page,
      limit,
      totalPages: Math.ceil(result.total / limit),
    };
  }

  @Get('stats')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async getAuditStats(@Query() query: AuditStatsDto) {
    const { startDate, endDate, performerId } = query;

    return this.auditService.getAuditStats({
      startDate: startDate ? new Date(startDate) : undefined,
      endDate: endDate ? new Date(endDate) : undefined,
      performerId,
    });
  }

  @Get('export')
  @RequireRoles(UserRole.ADMIN, UserRole.SUPER_ADMIN)
  @RequirePermissions({
    action: PermissionAction.READ,
    resource: PermissionResource.SYSTEM,
  })
  async exportAuditLogs(
    @Query() query: AuditLogExportDto,
    @Response() res: ExpressResponse,
  ) {
    const {
      performerId,
      userId,
      entityType,
      entityId,
      startDate,
      endDate,
      format = 'csv',
    } = query;

    const result = await this.auditService.getAuditLogs({
      skip: 0,
      take: 10000, // Limit export size
      performerId,
      userId,
      entityType,
      entityId,
      startDate: startDate ? new Date(startDate) : undefined,
      endDate: endDate ? new Date(endDate) : undefined,
    });

    if (format === 'csv') {
      // Convert to CSV
      const headers = [
        'ID',
        'Created At',
        'Performer',
        'Target User',
        'Action',
        'Entity Type',
        'Entity ID',
        'Old Values',
        'New Values',
      ];

      const rows = result.logs.map((log) => [
        log.id,
        log.created_at.toISOString(),
        log.performer?.username || '',
        log.user?.username || '',
        log.action,
        log.entity_type || '',
        log.entity_id || '',
        JSON.stringify(log.old_values || {}).replace(/"/g, '""'),
        JSON.stringify(log.new_values || {}).replace(/"/g, '""'),
      ]);

      const csvContent = [
        headers.join(','),
        ...rows.map((row) => row.map((cell) => `"${cell}"`).join(',')),
      ].join('\n');

      res.set({
        'Content-Type': 'text/csv',
        'Content-Disposition': `attachment; filename=audit-logs-${new Date().toISOString()}.csv`,
      });

      res.send(csvContent);
    } else {
      // JSON format
      res.set({
        'Content-Type': 'application/json',
        'Content-Disposition': `attachment; filename=audit-logs-${new Date().toISOString()}.json`,
      });

      res.json(result.logs);
    }
  }
}
