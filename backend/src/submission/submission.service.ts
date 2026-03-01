import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma.service';
import { JudgeService, JudgeTestCase, JudgeResult } from './judge.service';
import { SubmissionCrudService } from './services/submission-crud.service';
import { SubmissionQueryService } from './services/submission-query.service';
import { SubmissionExecutionService } from './services/submission-execution.service';

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
    problemId?: number | null,
    userId?: string,
    skip?: number,
    take?: number,
  ) {
    return this.queryService.findAll(problemId, userId, skip, take);
  }

  async findBest(problemId: number, userId: string) {
    return this.queryService.findBest(problemId, userId);
  }

  async getProblemStatusMap(userId: string, problemIds?: number[]) {
    return this.queryService.getProblemStatusMap(userId, problemIds);
  }

  async getDailyActivity(userId: string, year: number): Promise<string[]> {
    return this.queryService.getDailyActivity(userId, year);
  }

  async getSubmissionHistory(userId: string) {
    return this.queryService.getSubmissionHistory(userId);
  }

  async getLearningProgress(userId: string) {
    return this.queryService.getLearningProgress(userId);
  }

  async getStatusDefinitions(locale?: 'zh-CN' | 'en-US') {
    return this.queryService.getStatusDefinitions(locale);
  }

  async findOne(id: string, userId?: string) {
    return this.queryService.findOne(id, userId);
  }

  async getLatestRunResult(problemId: number, userId?: string) {
    return this.queryService.getLatestRunResult(problemId, userId);
  }

  async create(
    userId: string,
    problemId: number,
    data: { language: string; code: string },
  ) {
    return this.crudService.create(userId, problemId, data);
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
    problemId: number,
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
