import {
  Injectable,
  NotFoundException,
  ConflictException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { AuditService } from '../../admin/services/audit.service';
import {
  CreateReportDto,
  QueryReportsDto,
  ReportCategory,
  ReportStatus,
  ModerationStatus,
  ModeratableEntityType,
} from '../../common/dto/moderation.dto';
import {
  CategoryPriorityMap,
  EntityTypeModelMap,
} from '../../common/types/moderation.types';

@Injectable()
export class ReportService {
  constructor(
    private prisma: PrismaService,
    private audit: AuditService,
  ) {}

  async create(reporterId: string, dto: CreateReportDto) {
    // Check if entity exists and get author
    const entity = await this.getEntity(dto.entity_type, dto.entity_id);
    if (!entity) {
      throw new NotFoundException(
        `${dto.entity_type} with id ${dto.entity_id} not found`,
      );
    }

    // Check if user has already reported this entity
    const existingReport = await this.prisma.report.findFirst({
      where: {
        reporter_id: reporterId,
        entity_type: dto.entity_type,
        entity_id: dto.entity_id,
        status: ReportStatus.PENDING,
      },
    });

    if (existingReport) {
      throw new ConflictException('You have already reported this content');
    }

    // Get or create moderation queue entry
    let queueEntry = await this.prisma.moderationQueue.findUnique({
      where: {
        entity_type_entity_id: {
          entity_type: dto.entity_type,
          entity_id: dto.entity_id,
        },
      },
    });

    const priority =
      CategoryPriorityMap[dto.category as keyof typeof CategoryPriorityMap] ??
      0;

    if (!queueEntry) {
      queueEntry = await this.prisma.moderationQueue.create({
        data: {
          entity_type: dto.entity_type,
          entity_id: dto.entity_id,
          author_id: entity.author_id,
          priority,
          report_count: 1,
          primary_category: dto.category,
        },
      });
    } else {
      // Update existing queue entry
      queueEntry = await this.prisma.moderationQueue.update({
        where: { id: queueEntry.id },
        data: {
          report_count: { increment: 1 },
          priority: Math.max(queueEntry.priority, priority),
        },
      });
    }

    // Create the report
    const report = await this.prisma.report.create({
      data: {
        reporter_id: reporterId,
        entity_type: dto.entity_type,
        entity_id: dto.entity_id,
        category: dto.category,
        reason: dto.reason,
        evidence: dto.evidence,
        queue_id: queueEntry.id,
      },
    });

    // Flag the entity for backward compatibility
    await this.flagEntity(dto.entity_type, dto.entity_id, dto.reason);

    await this.audit.log({
      performerId: reporterId,
      action: 'CREATE_REPORT',
      entityType: dto.entity_type,
      entityId: dto.entity_id,
      newValues: { category: dto.category, reason: dto.reason },
    });

    return report;
  }

  async findAll(query: QueryReportsDto) {
    const {
      page = 1,
      limit = 20,
      status,
      category,
      entity_type,
      entity_id,
      reporter_id,
    } = query;
    const skip = (page - 1) * limit;

    const where: Record<string, unknown> = {};
    if (status) where.status = status;
    if (category) where.category = category;
    if (entity_type) where.entity_type = entity_type;
    if (entity_id) where.entity_id = entity_id;
    if (reporter_id) where.reporter_id = reporter_id;

    const [reports, total] = await Promise.all([
      this.prisma.report.findMany({
        where,
        skip,
        take: limit,
        orderBy: { created_at: 'desc' },
        include: {
          reporter: {
            select: { id: true, username: true, name: true, avatar: true },
          },
        },
      }),
      this.prisma.report.count({ where }),
    ]);

    return {
      data: reports,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async findOne(id: string) {
    const report = await this.prisma.report.findUnique({
      where: { id },
      include: {
        reporter: {
          select: { id: true, username: true, name: true, avatar: true },
        },
      },
    });

    if (!report) {
      throw new NotFoundException(`Report with id ${id} not found`);
    }

    return report;
  }

  async updateStatus(
    id: string,
    status: ReportStatus,
    adminId: string,
    queueId?: string,
  ) {
    const report = await this.findOne(id);

    const updated = await this.prisma.report.update({
      where: { id },
      data: {
        status,
        queue_id: queueId ?? report.queue_id,
      },
    });

    await this.audit.log({
      performerId: adminId,
      action: 'UPDATE_REPORT_STATUS',
      entityType: 'report',
      entityId: id,
      newValues: { status },
    });

    return updated;
  }

  async getReportsByEntity(
    entityType: ModeratableEntityType,
    entityId: string,
  ) {
    return this.prisma.report.findMany({
      where: {
        entity_type: entityType,
        entity_id: entityId,
      },
      orderBy: { created_at: 'desc' },
      include: {
        reporter: {
          select: { id: true, username: true, name: true, avatar: true },
        },
      },
    });
  }

  private async getEntity(entityType: ModeratableEntityType, entityId: string) {
    const modelName = EntityTypeModelMap[entityType];
    const model = this.prisma[
      modelName as keyof typeof this.prisma
    ] as unknown as {
      findUnique: (args: { where: { id: string } }) => Promise<{
        id: string;
        author_id?: string;
        user_id?: string;
      } | null>;
    };

    // Handle BigInt for problem
    const id = entityType === 'problem' ? BigInt(entityId) : entityId;

    const entity = await model.findUnique({
      where: { id: id as string },
    });

    if (!entity) return null;

    // Get author_id - some entities use user_id instead
    const authorId = entity.author_id ?? entity.user_id;
    if (!authorId) return null;

    return {
      id: entity.id,
      author_id: authorId,
    };
  }

  private async flagEntity(
    entityType: ModeratableEntityType,
    entityId: string,
    reason?: string,
  ) {
    const modelName = EntityTypeModelMap[entityType];
    const model = this.prisma[
      modelName as keyof typeof this.prisma
    ] as unknown as {
      update: (args: {
        where: { id: string };
        data: Record<string, unknown>;
      }) => Promise<unknown>;
    };

    const id = entityType === 'problem' ? BigInt(entityId) : entityId;

    await model.update({
      where: { id: id as string },
      data: {
        is_flagged: true,
        flagged_at: new Date(),
        ...(reason && { flagged_reason: reason }),
      },
    });
  }
}
