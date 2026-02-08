import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { JudgeService, JudgeTestCase, JudgeResult } from './judge.service';
import { SubmissionCrudService } from './services/submission-crud.service';
import { SubmissionQueryService } from './services/submission-query.service';
import { SubmissionExecutionService } from './services/submission-execution.service';
import { BigIntUtil } from '../common/utils/bigint.util';

@Injectable()
export class SubmissionService {
  constructor(
    private prisma: PrismaService,
    private judgeService: JudgeService,
    private readonly crudService: SubmissionCrudService,
    private readonly queryService: SubmissionQueryService,
    private readonly executionService: SubmissionExecutionService,
  ) {}

  async findAll(
    problemId?: bigint | string | number | null,
    userId?: string,
    skip?: number,
    take?: number,
  ) {
    return this.queryService.findAll(problemId, userId, skip, take);
  }

  async findBest(problemId: bigint | string | number, userId: string) {
    return this.queryService.findBest(problemId, userId);
  }

  async getProblemStatusMap(
    userId: string,
    problemIds?: (string | number | bigint)[],
  ) {
    return this.queryService.getProblemStatusMap(userId, problemIds);
  }

  async getDailyActivity(userId: string, year: number): Promise<string[]> {
    return this.queryService.getDailyActivity(userId, year);
  }

  async getStatusDefinitions(locale?: 'zh-CN' | 'en-US') {
    return this.queryService.getStatusDefinitions(locale);
  }

  async findOne(id: string, userId?: string) {
    return this.queryService.findOne(id, userId);
  }

  async getLatestRunResult(
    problemId: bigint | string | number,
    userId?: string,
  ) {
    return this.queryService.getLatestRunResult(problemId, userId);
  }

  async create(
    userId: string,
    problemId: bigint | string | number,
    data: { language: string; code: string },
  ) {
    const dbId = BigIntUtil.toBigInt(problemId);
    return this.crudService.create(userId, dbId, data);
  }

  async updateSubmissionAfterJudging(
    submissionId: string,
    judgeResult: JudgeResult,
  ) {
    const decorated = await this.crudService.updateSubmissionAfterJudging(
      submissionId,
      judgeResult,
    );
    return this.queryService.decorateSubmission(decorated);
  }

  async run(
    problemId: bigint | string | number,
    data: {
      language: string;
      code: string;
      testCases?: JudgeTestCase[];
    },
    userId?: string,
  ) {
    return this.executionService.run(problemId, data, userId);
  }
}
