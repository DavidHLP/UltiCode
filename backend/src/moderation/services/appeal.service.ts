import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { AuditService } from '../../admin/services/audit.service';
import {
  CreateAppealDto,
  QueryAppealsDto,
  ReviewAppealDto,
  AppealStatus,
  ModerationStatus,
  ModerationActionType,
} from '../../common/dto/moderation.dto';

@Injectable()
export class AppealService {
  constructor(
    private prisma: PrismaService,
    private audit: AuditService,
  ) {}

  async create(appellantId: string, dto: CreateAppealDto) {
    // Check if queue item exists and belongs to appellant
    const queue = await this.prisma.moderationQueue.findUnique({
      where: { id: dto.queue_id },
    });

    if (!queue) {
      throw new NotFoundException(
        `Moderation queue item with id ${dto.queue_id} not found`,
      );
    }

    if (queue.author_id !== appellantId) {
      throw new ForbiddenException(
        'You can only appeal your own content moderation',
      );
    }

    // Check if there's already a pending appeal
    const existingAppeal = await this.prisma.appeal.findFirst({
      where: {
        queue_id: dto.queue_id,
        status: AppealStatus.PENDING,
      },
    });

    if (existingAppeal) {
      throw new BadRequestException(
        'There is already a pending appeal for this content',
      );
    }

    // Create appeal and update queue status
    const appeal = await this.prisma.$transaction(async (tx) => {
      const newAppeal = await tx.appeal.create({
        data: {
          queue_id: dto.queue_id,
          appellant_id: appellantId,
          reason: dto.reason,
          evidence: dto.evidence,
        },
      });

      await tx.moderationQueue.update({
        where: { id: dto.queue_id },
        data: {
          status: ModerationStatus.APPEAL_PENDING,
        },
      });

      // Add action record
      await tx.moderationAction.create({
        data: {
          queue_id: dto.queue_id,
          action: ModerationActionType.APPEAL_PENDING,
          performed_by_id: appellantId,
          note: 'Appeal submitted',
        },
      });

      return newAppeal;
    });

    await this.audit.log({
      performerId: appellantId,
      action: 'CREATE_APPEAL',
      entityType: 'moderation_queue',
      entityId: dto.queue_id,
      newValues: { reason: dto.reason },
    });

    return appeal;
  }

  async findAll(query: QueryAppealsDto) {
    const { page = 1, limit = 20, status, queue_id, appellant_id } = query;
    const skip = (page - 1) * limit;

    const where: Record<string, unknown> = {};
    if (status) where.status = status;
    if (queue_id) where.queue_id = queue_id;
    if (appellant_id) where.appellant_id = appellant_id;

    const [appeals, total] = await Promise.all([
      this.prisma.appeal.findMany({
        where,
        skip,
        take: limit,
        orderBy: { created_at: 'desc' },
        include: {
          appellant: {
            select: { id: true, username: true, name: true, avatar: true },
          },
          queue: {
            include: {
              author: {
                select: { id: true, username: true, name: true },
              },
            },
          },
          reviewed_by: {
            select: { id: true, username: true, name: true },
          },
        },
      }),
      this.prisma.appeal.count({ where }),
    ]);

    return {
      data: appeals,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async findOne(id: string) {
    const appeal = await this.prisma.appeal.findUnique({
      where: { id },
      include: {
        appellant: {
          select: { id: true, username: true, name: true, avatar: true },
        },
        queue: {
          include: {
            author: {
              select: { id: true, username: true, name: true },
            },
            actions: {
              take: 10,
              orderBy: { created_at: 'desc' },
            },
          },
        },
        reviewed_by: {
          select: { id: true, username: true, name: true },
        },
      },
    });

    if (!appeal) {
      throw new NotFoundException(`Appeal with id ${id} not found`);
    }

    return appeal;
  }

  async review(id: string, adminId: string, dto: ReviewAppealDto) {
    const appeal = await this.findOne(id);

    if (appeal.status !== AppealStatus.PENDING) {
      throw new BadRequestException('This appeal has already been reviewed');
    }

    const result = await this.prisma.$transaction(async (tx) => {
      // Update appeal
      const updated = await tx.appeal.update({
        where: { id },
        data: {
          status: dto.status,
          reviewed_by_id: adminId,
          reviewed_at: new Date(),
          response: dto.response,
        },
      });

      // Update queue status and add action
      const newQueueStatus =
        dto.status === AppealStatus.APPROVED
          ? ModerationStatus.RESOLVED
          : ModerationStatus.RESOLVED; // Keep as resolved even if rejected

      await tx.moderationQueue.update({
        where: { id: appeal.queue_id },
        data: {
          status: newQueueStatus,
        },
      });

      await tx.moderationAction.create({
        data: {
          queue_id: appeal.queue_id,
          action:
            dto.status === AppealStatus.APPROVED
              ? ModerationActionType.APPEAL_APPROVED
              : ModerationActionType.APPEAL_REJECTED,
          performed_by_id: adminId,
          note: dto.response,
        },
      });

      // If approved, restore the content
      if (dto.status === AppealStatus.APPROVED) {
        // Update entity to unflag/unhide
        await this.restoreContent(
          tx,
          appeal.queue.entity_type,
          appeal.queue.entity_id,
        );
      }

      return updated;
    });

    await this.audit.log({
      performerId: adminId,
      action: `REVIEW_APPEAL_${dto.status}`,
      entityType: 'appeal',
      entityId: id,
      newValues: { response: dto.response },
    });

    return result;
  }

  async getAppealsByUser(userId: string) {
    return this.prisma.appeal.findMany({
      where: { appellant_id: userId },
      orderBy: { created_at: 'desc' },
      include: {
        queue: {
          select: {
            entity_type: true,
            entity_id: true,
            resolution: true,
          },
        },
      },
    });
  }

  async getStats() {
    const [byStatus, total, pending] = await Promise.all([
      this.prisma.appeal.groupBy({
        by: ['status'],
        _count: true,
      }),
      this.prisma.appeal.count(),
      this.prisma.appeal.count({
        where: { status: AppealStatus.PENDING },
      }),
    ]);

    return {
      total,
      pending,
      byStatus: byStatus.reduce(
        (acc, item) => {
          acc[item.status] = item._count;
          return acc;
        },
        {} as Record<string, number>,
      ),
    };
  }

  private async restoreContent(
    tx: Parameters<Parameters<typeof this.prisma.$transaction>[0]>[0],
    entityType: string,
    entityId: string,
  ) {
    const modelMap: Record<string, string> = {
      forum_post: 'forumPost',
      forum_comment: 'forumComment',
      solution: 'solution',
      solution_comment: 'solutionComment',
      problem: 'problem',
    };

    const modelName = modelMap[entityType];
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
        is_flagged: false,
        flagged_at: null,
        flagged_reason: null,
      },
    });
  }
}
