import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../prisma.service';
import { Submission, Prisma } from '@prisma/client';
import { InjectQueue } from '@nestjs/bullmq';
import { Queue } from 'bullmq';
import { JudgeJobData } from '../judge.processor';
import { JudgeResult } from '../judge.service';
import { v4 as uuid } from 'uuid';
import { CacheService } from '../../cache/cache.service';

@Injectable()
export class SubmissionCrudService {
  private readonly logger = new Logger(SubmissionCrudService.name);

  constructor(
    private readonly prisma: PrismaService,
    @InjectQueue('judge_queue') private judgeQueue: Queue<JudgeJobData>,
    private readonly cacheService: CacheService,
  ) {}

  async create(
    userId: string,
    problemId: bigint,
    data: { language: string; code: string },
  ): Promise<Submission> {
    const newSubmissionId = uuid();
    const created = await this.prisma.submission.create({
      data: {
        id: newSubmissionId,
        user_id: userId,
        problem_id: problemId,
        language: data.language,
        code: data.code,
        status: 'Pending',
        runtime: 0,
        memory: 0,
        runtime_percentile: null,
        memory_percentile: null,
        test_details: Prisma.DbNull as unknown as Prisma.InputJsonValue,
      },
    });

    await this.judgeQueue.add('judge', { submissionId: newSubmissionId });

    // Invalidate user stats cache
    await this.invalidateUserStatsCache(userId);

    return created;
  }

  async updateSubmissionAfterJudging(
    submissionId: string,
    judgeResult: JudgeResult,
  ): Promise<Submission> {
    const testDetailsJson =
      judgeResult.cases as unknown as Prisma.InputJsonValue;

    const updated = await this.prisma.submission.update({
      where: { id: submissionId },
      data: {
        status: judgeResult.verdict,
        runtime: judgeResult.runtime,
        memory: judgeResult.memory,
        test_details: testDetailsJson,
      },
      include: {
        problem: {
          select: {
            id: true,
            title: true,
            slug: true,
          },
        },
      },
    });

    // Invalidate user stats cache when submission is judged
    await this.invalidateUserStatsCache(updated.user_id);

    return updated;
  }

  private async invalidateUserStatsCache(userId: string): Promise<void> {
    try {
      await this.cacheService.del(`user_stats:${userId}`);
      this.logger.debug(`Invalidated user stats cache for user: ${userId}`);
    } catch (error) {
      this.logger.error(
        `Failed to invalidate user stats cache for user ${userId}:`,
        error,
      );
    }
  }
}
