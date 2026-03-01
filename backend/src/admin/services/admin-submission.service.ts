import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { Queue } from 'bullmq';
import { InjectQueue } from '@nestjs/bullmq';
import {
  AdminSubmissionQueryDto,
  AdminSubmissionDetail,
  SubmissionListResponse,
  BatchRejudgeDto,
  BatchRejudgeResponse,
  RejudgeResult,
} from '../dto/admin-submission.dto';

@Injectable()
export class AdminSubmissionService {
  private readonly logger = new Logger(AdminSubmissionService.name);

  constructor(
    private prisma: PrismaService,
    @InjectQueue('judge_queue') private judgeQueue: Queue,
  ) {}

  async findAll(
    query: AdminSubmissionQueryDto,
  ): Promise<SubmissionListResponse> {
    const {
      page = 1,
      limit = 20,
      userId,
      problemId,
      status,
      language,
      startDate,
      endDate,
      search,
      sortBy = 'created_at',
      sortOrder = 'desc',
    } = query;

    const skip = (page - 1) * limit;

    // Build where clause
    const where: Record<string, unknown> = {};

    if (userId) {
      where.user_id = userId;
    }

    if (problemId) {
      where.problem_id = BigInt(problemId);
    }

    if (status) {
      where.status = status;
    }

    if (language) {
      where.language = language;
    }

    if (startDate || endDate) {
      where.created_at = {};
      if (startDate) {
        (where.created_at as Record<string, Date>).gte = new Date(startDate);
      }
      if (endDate) {
        (where.created_at as Record<string, Date>).lte = new Date(endDate);
      }
    }

    // Search by username or problem title
    if (search) {
      where.OR = [
        {
          user: {
            username: { contains: search },
          },
        },
        {
          problem: {
            title: { contains: search },
          },
        },
      ];
    }

    // Get total count
    const total = await this.prisma.submission.count({ where });

    // Get submissions
    const submissions = await this.prisma.submission.findMany({
      where,
      skip,
      take: limit,
      orderBy: {
        [sortBy]: sortOrder,
      },
      select: {
        id: true,
        problem_id: true,
        user_id: true,
        language: true,
        status: true,
        runtime: true,
        memory: true,
        created_at: true,
        code: true,
        problem: {
          select: {
            title: true,
            slug: true,
          },
        },
        user: {
          select: {
            username: true,
          },
        },
      },
    });

    const data = submissions.map((sub) => ({
      id: sub.id,
      problemId: Number(sub.problem_id),
      problemTitle: sub.problem.title,
      problemSlug: sub.problem.slug,
      userId: sub.user_id,
      username: sub.user.username,
      language: sub.language,
      status: sub.status,
      runtime: sub.runtime,
      memory: sub.memory,
      createdAt: sub.created_at,
      codeLength: sub.code.length,
    }));

    return {
      data,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit),
    };
  }

  async findOne(id: string): Promise<AdminSubmissionDetail | null> {
    const submission = await this.prisma.submission.findUnique({
      where: { id },
      select: {
        id: true,
        problem_id: true,
        user_id: true,
        language: true,
        status: true,
        runtime: true,
        memory: true,
        created_at: true,
        code: true,
        notes: true,
        runtime_percentile: true,
        memory_percentile: true,
        test_details: true,
        memoryDistBinsMb: true,
        runtimeDistBinsMs: true,
        problem: {
          select: {
            title: true,
            slug: true,
          },
        },
        user: {
          select: {
            username: true,
          },
        },
      },
    });

    if (!submission) {
      return null;
    }

    return {
      id: submission.id,
      problemId: Number(submission.problem_id),
      problemTitle: submission.problem.title,
      problemSlug: submission.problem.slug,
      userId: submission.user_id,
      username: submission.user.username,
      language: submission.language,
      status: submission.status,
      runtime: submission.runtime,
      memory: submission.memory,
      createdAt: submission.created_at,
      codeLength: submission.code.length,
      code: submission.code,
      notes: submission.notes,
      runtimePercentile: submission.runtime_percentile,
      memoryPercentile: submission.memory_percentile,
      testDetails: submission.test_details,
      memoryDistBinsMb: submission.memoryDistBinsMb,
      runtimeDistBinsMs: submission.runtimeDistBinsMs,
    };
  }

  async rejudge(
    id: string,
    notifyUser: boolean = false,
  ): Promise<RejudgeResult> {
    const submission = await this.prisma.submission.findUnique({
      where: { id },
      select: { id: true, status: true, user_id: true },
    });

    if (!submission) {
      return {
        submissionId: id,
        success: false,
        oldStatus: '',
        error: 'Submission not found',
      };
    }

    const oldStatus = submission.status;

    try {
      // Reset status to pending
      await this.prisma.submission.update({
        where: { id },
        data: {
          status: 'PENDING',
          runtime: 0,
          memory: 0,
          test_details: undefined,
        },
      });

      // Add to judge queue
      await this.judgeQueue.add('judge', {
        submissionId: id,
        notifyUser,
      });

      this.logger.log(`Rejudge queued for submission ${id}`);

      return {
        submissionId: id,
        success: true,
        oldStatus,
      };
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Unknown error';
      this.logger.error(`Failed to rejudge submission ${id}: ${errorMessage}`);

      return {
        submissionId: id,
        success: false,
        oldStatus,
        error: errorMessage,
      };
    }
  }

  async batchRejudge(dto: BatchRejudgeDto): Promise<BatchRejudgeResponse> {
    const results: RejudgeResult[] = [];

    for (const id of dto.ids) {
      const result = await this.rejudge(id, dto.notifyUsers);
      results.push(result);
    }

    const successful = results.filter((r) => r.success).length;
    const failed = results.filter((r) => !r.success).length;

    return {
      results,
      total: dto.ids.length,
      successful,
      failed,
    };
  }

  async getStatuses(): Promise<
    Array<{ key: string; label: string; category: string }>
  > {
    const statuses = await this.prisma.submissionStatus.findMany({
      orderBy: { sort_order: 'asc' },
      select: {
        key: true,
        label: true,
        category: true,
      },
    });

    return statuses;
  }

  async getLanguages(): Promise<string[]> {
    const result = await this.prisma.submission.groupBy({
      by: ['language'],
      _count: {
        id: true,
      },
      orderBy: {
        _count: {
          id: 'desc',
        },
      },
    });

    return result.map((r) => r.language);
  }

  async getStatistics(): Promise<{
    total: number;
    byStatus: Array<{ status: string; count: number }>;
    byLanguage: Array<{ language: string; count: number }>;
    last24h: number;
    pending: number;
  }> {
    const [total, byStatus, byLanguage, last24h, pending] = await Promise.all([
      this.prisma.submission.count(),
      this.prisma.submission.groupBy({
        by: ['status'],
        _count: { id: true },
        orderBy: { _count: { id: 'desc' } },
      }),
      this.prisma.submission.groupBy({
        by: ['language'],
        _count: { id: true },
        orderBy: { _count: { id: 'desc' } },
        take: 10,
      }),
      this.prisma.submission.count({
        where: {
          created_at: {
            gte: new Date(Date.now() - 24 * 60 * 60 * 1000),
          },
        },
      }),
      this.prisma.submission.count({
        where: { status: 'PENDING' },
      }),
    ]);

    return {
      total,
      byStatus: byStatus.map((s) => ({ status: s.status, count: s._count.id })),
      byLanguage: byLanguage.map((l) => ({
        language: l.language,
        count: l._count.id,
      })),
      last24h,
      pending,
    };
  }
}
