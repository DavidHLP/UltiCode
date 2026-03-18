import {
  Injectable,
  NotFoundException,
  ForbiddenException,
  BadRequestException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { AuditService } from '../../admin/services/audit.service';
import { ModerationActionService } from './action.service';
import {
  QueryModerationQueueDto,
  AssignModerationDto,
  BatchModerationActionDto,
  ModerationStatus,
  ModerationActionType,
  ModeratableEntityType,
} from '../../common/dto/moderation.dto';
import { EntityTypeModelMap } from '../../common/types/moderation.types';
import { ModerationQueue, ReportCategory } from '@prisma/client';

type QueueItem = Pick<
  ModerationQueue,
  'id' | 'entity_type' | 'entity_id' | 'author_id' | 'primary_category'
>;

@Injectable()
export class ModerationQueueService {
  constructor(
    private prisma: PrismaService,
    private audit: AuditService,
    private actionService: ModerationActionService,
  ) {}

  async findAll(query: QueryModerationQueueDto) {
    const {
      page = 1,
      limit = 20,
      status,
      primary_category,
      entity_type,
      assigned_to_id,
      min_priority,
    } = query;
    const skip = (page - 1) * limit;

    const where: Record<string, unknown> = {};
    if (status) where.status = status;
    if (primary_category) where.primary_category = primary_category;
    if (entity_type) where.entity_type = entity_type;
    if (assigned_to_id) where.assigned_to_id = assigned_to_id;
    if (min_priority !== undefined) where.priority = { gte: min_priority };

    const [queue, total] = await Promise.all([
      this.prisma.moderationQueue.findMany({
        where,
        skip,
        take: limit,
        orderBy: [{ priority: 'desc' }, { created_at: 'asc' }],
        include: {
          author: {
            select: { id: true, username: true, name: true, avatar: true },
          },
          assigned_to: {
            select: { id: true, username: true, name: true },
          },
          actions: {
            take: 5,
            orderBy: { created_at: 'desc' },
            include: {
              performed_by: {
                select: { id: true, username: true, name: true },
              },
            },
          },
        },
      }),
      this.prisma.moderationQueue.count({ where }),
    ]);

    // Attach entity content for preview
    const queueWithContent = await Promise.all(
      queue.map(async (item) => {
        const entity = await this.getEntityPreview(
          item.entity_type as ModeratableEntityType,
          item.entity_id,
        );
        return { ...item, entity };
      }),
    );

    return {
      data: queueWithContent,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async findOne(id: string) {
    const queue = await this.prisma.moderationQueue.findUnique({
      where: { id },
      include: {
        author: {
          select: { id: true, username: true, name: true, avatar: true },
        },
        assigned_to: {
          select: { id: true, username: true, name: true },
        },
        reviewed_by: {
          select: { id: true, username: true, name: true },
        },
        actions: {
          orderBy: { created_at: 'desc' },
          include: {
            performed_by: {
              select: { id: true, username: true, name: true },
            },
          },
        },
      },
    });

    if (!queue) {
      throw new NotFoundException(
        `Moderation queue item with id ${id} not found`,
      );
    }

    // Get entity details
    const entity = await this.getEntityDetails(
      queue.entity_type as ModeratableEntityType,
      queue.entity_id,
    );

    return { ...queue, entity };
  }

  async findByEntity(entityType: ModeratableEntityType, entityId: string) {
    return this.prisma.moderationQueue.findUnique({
      where: {
        entity_type_entity_id: {
          entity_type: entityType,
          entity_id: entityId,
        },
      },
    });
  }

  async assign(id: string, dto: AssignModerationDto, adminId: string) {
    const queue = await this.findOne(id);

    if (queue.status !== ModerationStatus.PENDING) {
      throw new BadRequestException('Cannot assign a resolved queue item');
    }

    const updated = await this.prisma.moderationQueue.update({
      where: { id },
      data: {
        assigned_to_id: dto.assigned_to_id,
        assigned_at: new Date(),
        status: ModerationStatus.UNDER_REVIEW,
      },
    });

    await this.audit.log({
      performerId: adminId,
      action: 'ASSIGN_MODERATION',
      entityType: 'moderation_queue',
      entityId: id,
      newValues: { assigned_to_id: dto.assigned_to_id },
    });

    return updated;
  }

  async claim(id: string, adminId: string) {
    return this.assign(id, { assigned_to_id: adminId }, adminId);
  }

  async unassign(id: string, adminId: string) {
    const queue = await this.findOne(id);

    const updated = await this.prisma.moderationQueue.update({
      where: { id },
      data: {
        assigned_to_id: null,
        assigned_at: null,
        status: ModerationStatus.PENDING,
      },
    });

    await this.audit.log({
      performerId: adminId,
      action: 'UNASSIGN_MODERATION',
      entityType: 'moderation_queue',
      entityId: id,
    });

    return updated;
  }

  async performAction(
    id: string,
    action: ModerationActionType,
    adminId: string,
    note?: string,
    durationDays?: number,
  ) {
    const queue = await this.findOne(id);

    // Verify the admin is assigned (if assigned)
    if (queue.assigned_to_id && queue.assigned_to_id !== adminId) {
      throw new ForbiddenException(
        'This item is assigned to another moderator. Please claim it first.',
      );
    }

    // Perform the action
    const result = await this.actionService.performAction(
      queue,
      action,
      adminId,
      note,
      durationDays,
    );

    // Update queue status
    const newStatus = this.getStatusForAction(action);
    await this.prisma.moderationQueue.update({
      where: { id },
      data: {
        status: newStatus,
        reviewed_by_id: adminId,
        reviewed_at: new Date(),
        resolution: action,
        resolution_note: note,
        resolved_at: [
          ModerationStatus.RESOLVED,
          ModerationStatus.DISMISSED,
        ].includes(newStatus)
          ? new Date()
          : null,
      },
    });

    return result;
  }

  async batchAction(dto: BatchModerationActionDto, adminId: string) {
    const results = await Promise.all(
      dto.queue_ids.map((id) =>
        this.performAction(id, dto.action, adminId, dto.note).catch((err) => ({
          id,
          error: err.message,
        })),
      ),
    );

    return {
      processed: results.filter((r) => !('error' in r)).length,
      failed: results.filter((r) => 'error' in r).length,
      results,
    };
  }

  async getStats() {
    const [byStatus, byCategory, total, pendingHighPriority] =
      await Promise.all([
        this.prisma.moderationQueue.groupBy({
          by: ['status'],
          _count: true,
        }),
        this.prisma.moderationQueue.groupBy({
          by: ['primary_category'],
          _count: true,
          where: { status: ModerationStatus.PENDING },
        }),
        this.prisma.moderationQueue.count(),
        this.prisma.moderationQueue.count({
          where: {
            status: ModerationStatus.PENDING,
            priority: { gte: 2 },
          },
        }),
      ]);

    return {
      total,
      pendingHighPriority,
      byStatus: byStatus.reduce(
        (acc, item) => {
          acc[item.status] = item._count;
          return acc;
        },
        {} as Record<string, number>,
      ),
      byCategory: byCategory.reduce(
        (acc, item) => {
          if (item.primary_category) {
            acc[item.primary_category] = item._count;
          }
          return acc;
        },
        {} as Record<string, number>,
      ),
    };
  }

  private getStatusForAction(action: ModerationActionType): ModerationStatus {
    switch (action) {
      case ModerationActionType.DISMISSED:
        return ModerationStatus.DISMISSED;
      case ModerationActionType.RESOLVED:
      case ModerationActionType.DELETED:
      case ModerationActionType.HIDDEN:
      case ModerationActionType.WARNED:
      case ModerationActionType.TEMP_BANNED:
      case ModerationActionType.PERM_BANNED:
        return ModerationStatus.RESOLVED;
      case ModerationActionType.APPEAL_PENDING:
        return ModerationStatus.APPEAL_PENDING;
      default:
        return ModerationStatus.UNDER_REVIEW;
    }
  }

  private async getEntityPreview(
    entityType: ModeratableEntityType,
    entityId: string,
  ) {
    const modelName = EntityTypeModelMap[entityType];
    const model = this.prisma[
      modelName as keyof typeof this.prisma
    ] as unknown as {
      findUnique: (args: {
        where: { id: string };
      }) => Promise<Record<string, unknown> | null>;
    };

    const id = entityType === 'problem' ? BigInt(entityId) : entityId;
    const entity = await model.findUnique({
      where: { id: id as string },
    });

    if (!entity) return null;

    // Return preview based on entity type
    switch (entityType) {
      case 'forum_post':
        return {
          title: entity.title,
          excerpt: entity.excerpt,
          created_at: entity.created_at,
        };
      case 'forum_comment':
        return {
          body: (entity.body as string)?.slice(0, 200),
          created_at: entity.created_at,
        };
      case 'solution':
        return {
          title: entity.title,
          summary: entity.summary,
          created_at: entity.created_at,
        };
      case 'solution_comment':
        return {
          content: (entity.content as string)?.slice(0, 200),
          created_at: entity.created_at,
        };
      case 'problem':
        return {
          title: entity.title,
          slug: entity.slug,
          difficulty: entity.difficulty,
        };
      default:
        return entity;
    }
  }

  private async getEntityDetails(
    entityType: ModeratableEntityType,
    entityId: string,
  ) {
    const modelName = EntityTypeModelMap[entityType];
    const model = this.prisma[
      modelName as keyof typeof this.prisma
    ] as unknown as {
      findUnique: (args: {
        where: { id: string };
      }) => Promise<Record<string, unknown> | null>;
    };

    const id = entityType === 'problem' ? BigInt(entityId) : entityId;
    return model.findUnique({
      where: { id: id as string },
    });
  }
}
