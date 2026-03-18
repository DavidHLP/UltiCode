import { Injectable, BadRequestException } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { AuditService } from '../../admin/services/audit.service';
import { ModerationActionType } from '../../common/dto/moderation.dto';
import { EntityTypeModelMap } from '../../common/types/moderation.types';
import { ModerationQueue, ReportCategory } from '@prisma/client';

type QueueItem = Pick<
  ModerationQueue,
  'id' | 'entity_type' | 'entity_id' | 'author_id' | 'primary_category'
>;

@Injectable()
export class ModerationActionService {
  constructor(
    private prisma: PrismaService,
    private audit: AuditService,
  ) {}

  async performAction(
    queue: QueueItem,
    action: ModerationActionType,
    adminId: string,
    note?: string,
    durationDays?: number,
  ) {
    // Perform the action in a transaction
    return this.prisma.$transaction(async (tx) => {
      // Create action record
      const actionRecord = await tx.moderationAction.create({
        data: {
          queue_id: queue.id,
          action,
          performed_by_id: adminId,
          note,
          duration_days: durationDays,
        },
      });

      // Apply the actual action
      switch (action) {
        case ModerationActionType.DELETED:
          await this.deleteEntity(
            tx,
            queue.entity_type,
            queue.entity_id,
            adminId,
          );
          break;

        case ModerationActionType.HIDDEN:
          await this.hideEntity(tx, queue.entity_type, queue.entity_id);
          break;

        case ModerationActionType.RESTORED:
          await this.restoreEntity(
            tx,
            queue.entity_type,
            queue.entity_id,
            adminId,
          );
          break;

        case ModerationActionType.WARNED:
          await this.warnUser(
            tx,
            queue.author_id,
            queue,
            actionRecord.id,
            note,
          );
          break;

        case ModerationActionType.TEMP_BANNED:
          await this.banUser(
            tx,
            queue.author_id,
            queue,
            actionRecord.id,
            false,
            durationDays ?? 7,
            adminId,
            note,
          );
          break;

        case ModerationActionType.PERM_BANNED:
          await this.banUser(
            tx,
            queue.author_id,
            queue,
            actionRecord.id,
            true,
            undefined,
            adminId,
            note,
          );
          break;

        case ModerationActionType.DISMISSED:
        case ModerationActionType.RESOLVED:
          await this.unflagEntity(tx, queue.entity_type, queue.entity_id);
          break;

        default:
          break;
      }

      await this.audit.log({
        performerId: adminId,
        action: `MODERATION_${action}`,
        entityType: queue.entity_type,
        entityId: queue.entity_id,
        newValues: { note, duration_days: durationDays },
      });

      return actionRecord;
    });
  }

  async getActionsByQueue(queueId: string) {
    return this.prisma.moderationAction.findMany({
      where: { queue_id: queueId },
      orderBy: { created_at: 'desc' },
      include: {
        performed_by: {
          select: { id: true, username: true, name: true, avatar: true },
        },
      },
    });
  }

  async getActionsByUser(userId: string) {
    return this.prisma.moderationAction.findMany({
      where: { performed_by_id: userId },
      orderBy: { created_at: 'desc' },
      take: 50,
    });
  }

  private async deleteEntity(
    tx: Parameters<Parameters<typeof this.prisma.$transaction>[0]>[0],
    entityType: string,
    entityId: string,
    adminId: string,
  ) {
    const modelName =
      EntityTypeModelMap[entityType as keyof typeof EntityTypeModelMap];
    const model = tx[modelName as keyof typeof tx] as unknown as {
      update: (args: {
        where: { id: string };
        data: Record<string, unknown>;
      }) => Promise<unknown>;
    };

    const id = entityType === 'problem' ? BigInt(entityId) : entityId;

    await model.update({
      where: { id: id as string },
      data: {
        is_deleted: true,
        deleted_at: new Date(),
        deleted_by: adminId,
      },
    });
  }

  private async hideEntity(
    tx: Parameters<Parameters<typeof this.prisma.$transaction>[0]>[0],
    entityType: string,
    entityId: string,
  ) {
    // For now, hiding is the same as soft delete but with a different flag
    // Could add is_hidden field in the future
    const modelName =
      EntityTypeModelMap[entityType as keyof typeof EntityTypeModelMap];
    const model = tx[modelName as keyof typeof tx] as unknown as {
      update: (args: {
        where: { id: string };
        data: Record<string, unknown>;
      }) => Promise<unknown>;
    };

    const id = entityType === 'problem' ? BigInt(entityId) : entityId;

    await model.update({
      where: { id: id as string },
      data: {
        is_deleted: true,
        deleted_at: new Date(),
      },
    });
  }

  private async restoreEntity(
    tx: Parameters<Parameters<typeof this.prisma.$transaction>[0]>[0],
    entityType: string,
    entityId: string,
    adminId: string,
  ) {
    const modelName =
      EntityTypeModelMap[entityType as keyof typeof EntityTypeModelMap];
    const model = tx[modelName as keyof typeof tx] as unknown as {
      update: (args: {
        where: { id: string };
        data: Record<string, unknown>;
      }) => Promise<unknown>;
    };

    const id = entityType === 'problem' ? BigInt(entityId) : entityId;

    await model.update({
      where: { id: id as string },
      data: {
        is_deleted: false,
        deleted_at: null,
        deleted_by: null,
      },
    });
  }

  private async unflagEntity(
    tx: Parameters<Parameters<typeof this.prisma.$transaction>[0]>[0],
    entityType: string,
    entityId: string,
  ) {
    const modelName =
      EntityTypeModelMap[entityType as keyof typeof EntityTypeModelMap];
    const model = tx[modelName as keyof typeof tx] as unknown as {
      update: (args: {
        where: { id: string };
        data: Record<string, unknown>;
      }) => Promise<unknown>;
    };

    const id = entityType === 'problem' ? BigInt(entityId) : entityId;

    await model.update({
      where: { id: id as string },
      data: {
        is_flagged: false,
        flagged_at: null,
        flagged_reason: null,
      },
    });
  }

  private async warnUser(
    tx: Parameters<Parameters<typeof this.prisma.$transaction>[0]>[0],
    userId: string,
    queue: QueueItem,
    actionId: string,
    note?: string,
  ) {
    await tx.userWarning.create({
      data: {
        user_id: userId,
        queue_id: queue.id,
        action_id: actionId,
        reason: note ?? 'Content violation',
        category: queue.primary_category ?? ReportCategory.OTHER,
      },
    });
  }

  private async banUser(
    tx: Parameters<Parameters<typeof this.prisma.$transaction>[0]>[0],
    userId: string,
    queue: QueueItem,
    actionId: string,
    isPermanent: boolean,
    durationDays?: number,
    adminId?: string,
    note?: string,
  ) {
    const now = new Date();
    const endsAt = durationDays
      ? new Date(now.getTime() + durationDays * 24 * 60 * 60 * 1000)
      : null;

    // Create ban record
    await tx.userBan.create({
      data: {
        user_id: userId,
        is_permanent: isPermanent,
        reason: note ?? 'Content violation',
        category: queue.primary_category ?? ReportCategory.OTHER,
        queue_id: queue.id,
        action_id: actionId,
        banned_by_id: adminId ?? '',
        ends_at: endsAt,
      },
    });

    // Update user's ban status
    await tx.user.update({
      where: { id: userId },
      data: {
        is_banned: true,
        banned_until: isPermanent ? null : endsAt,
        banned_reason: note ?? 'Content violation',
      },
    });
  }
}
