import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { AuditService } from '../../admin/services/audit.service';
import type {
  FlagUpdateData,
  UnflagUpdateData,
  SoftDeleteUpdateData,
  RestoreUpdateData,
} from '../types/moderation.types';

type PrismaModel = 'forumPost' | 'solution' | 'problem' | 'contest';

@Injectable()
export class ModerationService {
  constructor(
    private prisma: PrismaService,
    private audit: AuditService,
  ) {}

  // 标记实体
  async flag(
    model: PrismaModel,
    id: string,
    reason: string | undefined,
    adminId: string,
    entityType: string,
  ): Promise<unknown> {
    const data: FlagUpdateData = {
      is_flagged: true,
      flagged_at: new Date(),
      ...(reason && { flagged_reason: reason }),
    };

    const entity = await (
      this.prisma[model] as unknown as {
        update: (args: {
          where: { id: string };
          data: Record<string, unknown>;
        }) => Promise<unknown>;
      }
    ).update({
      where: { id },
      data: data as Record<string, unknown>,
    });
    await this.audit.log({
      performerId: adminId,
      action: `FLAG_${entityType}`,
      entityType,
      entityId: id,
      newValues: { reason },
    });
    return entity;
  }

  // 取消标记
  async unflag(
    model: PrismaModel,
    id: string,
    adminId: string,
    entityType: string,
  ): Promise<unknown> {
    const data: UnflagUpdateData = {
      is_flagged: false,
      flagged_at: null,
      flagged_reason: null,
    };

    const entity = await (
      this.prisma[model] as unknown as {
        update: (args: {
          where: { id: string };
          data: Record<string, unknown>;
        }) => Promise<unknown>;
      }
    ).update({
      where: { id },
      data: data as Record<string, unknown>,
    });
    await this.audit.log({
      performerId: adminId,
      action: `UNFLAG_${entityType}`,
      entityType,
      entityId: id,
    });
    return entity;
  }

  // 软删除
  async softDelete(
    model: PrismaModel,
    id: string,
    adminId: string,
    entityType: string,
  ): Promise<unknown> {
    const data: SoftDeleteUpdateData = {
      is_deleted: true,
      deleted_at: new Date(),
      deleted_by: adminId,
    };

    const entity = await (
      this.prisma[model] as unknown as {
        update: (args: {
          where: { id: string };
          data: Record<string, unknown>;
        }) => Promise<unknown>;
      }
    ).update({
      where: { id },
      data: data as Record<string, unknown>,
    });
    await this.audit.log({
      performerId: adminId,
      action: `DELETE_${entityType}`,
      entityType,
      entityId: id,
    });
    return entity;
  }

  // 恢复
  async restore(
    model: PrismaModel,
    id: string,
    adminId: string,
    entityType: string,
  ): Promise<unknown> {
    const data: RestoreUpdateData = {
      is_deleted: false,
      deleted_at: null,
      deleted_by: null,
    };

    const entity = await (
      this.prisma[model] as unknown as {
        update: (args: {
          where: { id: string };
          data: Record<string, unknown>;
        }) => Promise<unknown>;
      }
    ).update({
      where: { id },
      data: data as Record<string, unknown>,
    });
    await this.audit.log({
      performerId: adminId,
      action: `RESTORE_${entityType}`,
      entityType,
      entityId: id,
    });
    return entity;
  }

  // 应用默认过滤（排除已删除项）
  applyDefaultModerationFilter<T>(where: T, includeDeleted?: boolean): T {
    if (!includeDeleted) return { ...where, is_deleted: false } as T;
    return where;
  }
}
