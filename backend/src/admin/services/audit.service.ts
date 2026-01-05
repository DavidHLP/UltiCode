import { Injectable } from '@nestjs/common';
import { AuditLogData } from '../types/admin.types';
import { AuditLog, Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma.service';

@Injectable()
export class AuditService {
  constructor(private prisma: PrismaService) {}

  /**
   * Create an audit log entry
   */
  log(data: AuditLogData): Promise<AuditLog> {
    return this.prisma.auditLog.create({
      data: {
        performer_id: data.performerId,
        user_id: data.userId,
        action: data.action,
        entity_type: data.entityType,
        entity_id: data.entityId || 'N/A',
        old_values: data.oldValues ?? Prisma.JsonNull,
        new_values: data.newValues ?? Prisma.JsonNull,
        ip_address: data.ipAddress,
        user_agent: data.userAgent,
      },
    });
  }

  /**
   * Get audit logs with filters
   */
  async getAuditLogs(params: {
    skip?: number;
    take?: number;
    performerId?: string;
    userId?: string;
    entityType?: string;
    entityId?: string;
    startDate?: Date;
    endDate?: Date;
  }) {
    const {
      skip = 0,
      take = 50,
      performerId,
      userId,
      entityType,
      entityId,
      startDate,
      endDate,
    } = params;

    const where: Prisma.AuditLogWhereInput = {};

    if (performerId) {
      where.performer_id = performerId;
    }

    if (userId) {
      where.user_id = userId;
    }

    if (entityType) {
      where.entity_type = entityType;
    }

    if (entityId) {
      where.entity_id = entityId;
    }

    if (startDate || endDate) {
      where.created_at = {};
      if (startDate) {
        where.created_at.gte = startDate;
      }
      if (endDate) {
        where.created_at.lte = endDate;
      }
    }

    const [logs, total] = await Promise.all([
      this.prisma.auditLog.findMany({
        where,
        skip,
        take,
        orderBy: { created_at: 'desc' },
        include: {
          performer: {
            select: {
              id: true,
              username: true,
              name: true,
              role: true,
            },
          },
          user: {
            select: {
              id: true,
              username: true,
              name: true,
            },
          },
        },
      }),
      this.prisma.auditLog.count({ where }),
    ]);

    return { logs, total };
  }

  /**
   * Get audit statistics
   */
  async getAuditStats(params: {
    startDate?: Date;
    endDate?: Date;
    performerId?: string;
  }) {
    const { startDate, endDate, performerId } = params;

    const where: Prisma.AuditLogWhereInput = {};

    if (startDate || endDate) {
      where.created_at = {};
      if (startDate) {
        where.created_at.gte = startDate;
      }
      if (endDate) {
        where.created_at.lte = endDate;
      }
    }

    if (performerId) {
      where.performer_id = performerId;
    }

    const [totalActions, actionsByEntity, actionsByPerformer, topPerformers] =
      await Promise.all([
        this.prisma.auditLog.count({ where }),
        this.prisma.auditLog.groupBy({
          by: ['entity_type'],
          where,
          _count: true,
          orderBy: { _count: { entity_type: 'desc' } },
        }),
        this.prisma.auditLog.groupBy({
          by: ['performer_id'],
          where,
          _count: true,
          orderBy: { _count: { performer_id: 'desc' } },
          take: 10,
        }),
        this.prisma.auditLog.findMany({
          where,
          select: {
            performer: {
              select: {
                id: true,
                username: true,
                name: true,
                role: true,
              },
            },
          },
          distinct: ['performer_id'],
          take: 10,
        }),
      ]);

    return {
      totalActions,
      actionsByEntity: actionsByEntity.map((item) => ({
        entityType: item.entity_type,
        count: item._count,
      })),
      actionsByPerformer: actionsByPerformer.map((item) => ({
        performerId: item.performer_id,
        count: item._count,
      })),
      topPerformers,
    };
  }
}
